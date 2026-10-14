package com.fvstexpress.driver.data.offline

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fvstexpress.driver.BuildConfig
import com.fvstexpress.driver.FvstApp
import com.fvstexpress.driver.data.model.PingHistoryRequest
import com.fvstexpress.driver.data.model.PingPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * GPS pings that couldn't be sent (no signal), kept on disk one JSON line each and sent later in
 * batches to POST /presence/history by [PingBacklogWorker], so the driven-route history has no gaps.
 * Separate from [OfflineQueue]: these aren't driver taps and don't belong in the "saved offline" banner.
 */
class PingBacklog(private val context: Context, private val json: Json) {
    private val file = File(context.filesDir, "ping_backlog.jsonl")
    private val mutex = Mutex()

    private fun read(): List<PingPoint> =
        if (!file.exists()) emptyList()
        else file.readLines().mapNotNull { line -> runCatching { json.decodeFromString(PingPoint.serializer(), line) }.getOrNull() }

    suspend fun add(point: PingPoint) {
        mutex.withLock {
            file.appendText(json.encodeToString(PingPoint.serializer(), point) + "\n")
            // About 20 hours of pings at one per 15 s; past that the oldest are dropped.
            val all = read()
            if (all.size > MAX_POINTS) file.writeText(all.takeLast(MAX_POINTS).joinToString("") { json.encodeToString(PingPoint.serializer(), it) + "\n" })
        }
        scheduleSync()
    }

    suspend fun isEmpty(): Boolean = mutex.withLock { !file.exists() || file.length() == 0L }

    suspend fun peek(n: Int): List<PingPoint> = mutex.withLock { read().take(n) }

    /** Drops the first [n] points (the batch the server just took). */
    suspend fun removeFirst(n: Int) {
        mutex.withLock {
            val rest = read().drop(n)
            if (rest.isEmpty()) file.delete() else file.writeText(rest.joinToString("") { json.encodeToString(PingPoint.serializer(), it) + "\n" })
        }
    }

    /** KEEP: while offline a send is already waiting for the network, no need to stack more. */
    fun scheduleSync() {
        val request = OneTimeWorkRequestBuilder<PingBacklogWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("ping-backlog-sync", ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val MAX_POINTS = 5000
        const val BATCH = 200
    }
}

/**
 * Sends [PingBacklog] oldest first, [PingBacklog.BATCH] at a time. A network failure or 5xx retries
 * later; a 4xx (e.g. logged out) drops that batch, as the server will never take it. A batch resent
 * after a lost answer is harmless: the server skips points it already has.
 */
class PingBacklogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val container = (applicationContext as FvstApp).container
        val backlog = container.pingBacklog
        var batch = backlog.peek(PingBacklog.BATCH)
        while (batch.isNotEmpty()) {
            if (!BuildConfig.USE_MOCK_API) {
                try {
                    container.api.presenceHistory(PingHistoryRequest(batch))
                } catch (e: HttpException) {
                    if (e.code() >= 500) return@withContext Result.retry()
                } catch (e: IOException) {
                    return@withContext Result.retry()
                }
            }
            backlog.removeFirst(batch.size)
            batch = backlog.peek(PingBacklog.BATCH)
        }
        Result.success()
    }
}
