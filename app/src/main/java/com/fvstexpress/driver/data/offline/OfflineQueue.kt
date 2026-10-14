package com.fvstexpress.driver.data.offline

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * A write the server hasn't confirmed yet (no signal). Kept on disk in tap order and replayed by
 * [SyncWorker]. The Idempotency-Key is fixed when the tap happens, so a replay the server already
 * applied is ignored instead of applied twice.
 */
@Serializable
data class QueuedCall(
    val id: String = UUID.randomUUID().toString(),
    /** "json" or "photo" (multipart upload of [filePath]). */
    val kind: String,
    val method: String,
    /** Relative to the API base URL, e.g. "stops/D-2/delivered". */
    val path: String,
    val bodyJson: String? = null,
    val filePath: String? = null,
    val form: Map<String, String> = emptyMap(),
    val idempotencyKey: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val label: String = "",
)

class OfflineQueue(private val context: Context, private val json: Json) {
    private val file = File(context.filesDir, "offline_queue.json")
    private val mutex = Mutex()
    private val serializer = ListSerializer(QueuedCall.serializer())

    private val _pending = MutableStateFlow(read())
    /** What's waiting to sync, for the "saved offline" banner. */
    val pending: StateFlow<List<QueuedCall>> = _pending.asStateFlow()

    private fun read(): List<QueuedCall> =
        if (!file.exists()) emptyList() else runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())

    private fun write(list: List<QueuedCall>) {
        file.writeText(json.encodeToString(serializer, list))
        _pending.value = list
    }

    suspend fun add(call: QueuedCall) {
        mutex.withLock { write(read() + call) }
        scheduleSync()
    }

    suspend fun peekAll(): List<QueuedCall> = mutex.withLock { read() }

    suspend fun remove(id: String) {
        mutex.withLock { write(read().filterNot { it.id == id }) }
    }

    suspend fun clear() {
        mutex.withLock { write(emptyList()) }
    }

    fun scheduleSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("offline-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
