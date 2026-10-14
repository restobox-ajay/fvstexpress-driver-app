package com.fvstexpress.driver.data.offline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fvstexpress.driver.BuildConfig
import com.fvstexpress.driver.FvstApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException

/**
 * Replays [OfflineQueue] in order. Stops at the first network failure (WorkManager retries later).
 * A 4xx means the server rejected that tap for good (e.g. the order was cancelled meanwhile), so it
 * is dropped and the rest continue; the screen reloads from the server and shows the real state.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val container = (applicationContext as FvstApp).container
        if (BuildConfig.USE_MOCK_API) {
            container.offlineQueue.clear()
            return@withContext Result.success()
        }
        val client = container.authedClient
        val base = BuildConfig.API_BASE_URL.trimEnd('/') + "/"
        val jsonType = "application/json".toMediaType()

        for (call in container.offlineQueue.peekAll()) {
            val body = when (call.kind) {
                "photo" -> {
                    val f = call.filePath?.let { File(it) }
                    if (f == null || !f.exists()) { container.offlineQueue.remove(call.id); continue }
                    MultipartBody.Builder().setType(MultipartBody.FORM).apply {
                        call.form.forEach { (k, v) -> addFormDataPart(k, v) }
                        addFormDataPart("file", f.name, f.asRequestBody("image/jpeg".toMediaType()))
                    }.build()
                }
                else -> (call.bodyJson ?: "{}").toRequestBody(jsonType)
            }
            val request = Request.Builder()
                .url(base + call.path)
                .method(call.method, body)
                .header("Idempotency-Key", call.idempotencyKey)
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    when {
                        resp.isSuccessful || resp.code in 400..499 -> container.offlineQueue.remove(call.id)
                        else -> return@withContext Result.retry() // 5xx: server trouble, try again later
                    }
                }
            } catch (e: IOException) {
                return@withContext Result.retry()
            }
        }
        Result.success()
    }
}
