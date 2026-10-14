package com.fvstexpress.driver.data.repo

import com.fvstexpress.driver.data.api.ApiFactory
import com.fvstexpress.driver.data.api.DriverApi
import com.fvstexpress.driver.data.location.LocationHolder
import com.fvstexpress.driver.data.model.AnalyticsSummary
import com.fvstexpress.driver.data.model.AppIssueRequest
import com.fvstexpress.driver.data.model.DeliveredRequest
import com.fvstexpress.driver.data.model.DriverDto
import com.fvstexpress.driver.data.model.ExceptionRequest
import com.fvstexpress.driver.data.model.FailedRequest
import com.fvstexpress.driver.data.model.FailedResponse
import com.fvstexpress.driver.data.model.FailureReason
import com.fvstexpress.driver.data.model.LeftBehind
import com.fvstexpress.driver.data.model.LoginRequest
import com.fvstexpress.driver.data.model.MessagesResponse
import com.fvstexpress.driver.data.model.OrderDetail
import com.fvstexpress.driver.data.model.PickedUpRequest
import com.fvstexpress.driver.data.model.PickedUpResponse
import com.fvstexpress.driver.data.model.PingResponse
import com.fvstexpress.driver.data.model.PresenceRequest
import com.fvstexpress.driver.data.model.RefreshRequest
import com.fvstexpress.driver.data.model.RouteResponse
import com.fvstexpress.driver.data.model.StarRequest
import com.fvstexpress.driver.data.model.StatusActionRequest
import com.fvstexpress.driver.data.model.StatusResponse
import com.fvstexpress.driver.data.model.StopDetail
import com.fvstexpress.driver.data.model.StopsResponse
import com.fvstexpress.driver.data.model.UndoRequest
import com.fvstexpress.driver.data.model.WorkRequest
import com.fvstexpress.driver.data.model.WorkResponse
import com.fvstexpress.driver.data.model.WorkToday
import com.fvstexpress.driver.data.offline.OfflineQueue
import com.fvstexpress.driver.data.offline.QueuedCall
import com.fvstexpress.driver.data.session.SessionStore
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.UUID

sealed interface ApiResult<out T> {
    data class Ok<T>(val data: T) : ApiResult<T>
    /** No signal: the tap is saved on the phone and will be sent when the connection is back. */
    data object Queued : ApiResult<Nothing>
    data class Err(val message: String, val code: Int? = null, val fieldErrors: Map<String, String> = emptyMap()) : ApiResult<Nothing>
}

class DriverRepository(
    private val api: DriverApi,
    private val session: SessionStore,
    private val queue: OfflineQueue,
) {
    private val json = ApiFactory.json

    /** Whole seconds: the server parses times as ISO 8601 without fractions (e.g. 2026-10-14T09:05:00-07:00). */
    private fun now(): String = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    private fun gps(): Pair<Double?, Double?> = LocationHolder.last.value.let { it?.latitude to it?.longitude }
    private fun action(): StatusActionRequest { val (lat, lng) = gps(); return StatusActionRequest(now(), lat, lng) }

    private fun errorFrom(e: HttpException): ApiResult.Err {
        val raw = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty()
        val obj: JsonObject? = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
        val fields = runCatching {
            obj?.get("errors")?.jsonObject?.mapValues { it.value.jsonPrimitive.content }
        }.getOrNull().orEmpty()
        val msg = runCatching { obj?.get("error")?.jsonPrimitive?.content }.getOrNull()
            ?: fields.values.firstOrNull()
            ?: when (e.code()) {
                401 -> "Your session expired. Please log in again."
                403 -> "You're offline or not allowed to do this."
                404 -> "Not found — it may have been reassigned."
                409 -> "This stop changed. Pull down to refresh."
                else -> "Server error (${e.code()})."
            }
        return ApiResult.Err(msg, e.code(), fields)
    }

    private suspend fun <T> call(block: suspend () -> T): ApiResult<T> = try {
        ApiResult.Ok(block())
    } catch (e: HttpException) {
        errorFrom(e)
    } catch (e: IOException) {
        ApiResult.Err("No connection to Dispatch. Check your signal and try again.")
    } catch (e: Exception) {
        ApiResult.Err(e.message ?: "Something went wrong.")
    }

    /** Writes that must not be lost: on no signal they go to the offline queue instead of failing. */
    private suspend inline fun <reified B, T> write(path: String, body: B, label: String, crossinline block: suspend () -> T): ApiResult<T> = try {
        ApiResult.Ok(block())
    } catch (e: HttpException) {
        errorFrom(e)
    } catch (e: IOException) {
        val ser: KSerializer<B> = serializer()
        queue.add(QueuedCall(kind = "json", method = "POST", path = path, bodyJson = json.encodeToString(ser, body), label = label))
        ApiResult.Queued
    }

    val pending get() = queue.pending

    // ---------- Auth ----------
    suspend fun login(login: String, pin: String): ApiResult<DriverDto?> = call {
        val t = api.login(LoginRequest(login.trim(), pin, session.deviceId))
        session.save(t.accessToken, t.refreshToken, t.driver)
        t.driver
    }

    suspend fun logout() {
        session.refreshToken?.let { runCatching { api.logout(RefreshRequest(it)) } }
        session.clear()
    }

    // ---------- Driver ----------
    suspend fun me(): ApiResult<DriverDto> = call { api.me().also { session.updateDriver(it) } }

    suspend fun setOnline(online: Boolean): ApiResult<Boolean> = call {
        val (lat, lng) = gps()
        api.presence(PresenceRequest(online, lat, lng)).online.also { on ->
            session.session.value?.driver?.let { session.updateDriver(it.copy(online = on)) }
        }
    }

    suspend fun sendPing(lat: Double, lng: Double) { api.presence(PresenceRequest(true, lat, lng)) }

    suspend fun work(event: String): ApiResult<WorkResponse> = write("work", WorkRequest(event, now()), "Hours: $event") { api.work(WorkRequest(event, now())) }
    suspend fun workToday(): ApiResult<WorkToday> = call { api.workToday() }

    // ---------- Tasks ----------
    suspend fun stops(view: String): ApiResult<StopsResponse> = call { api.stops(view) }
    suspend fun route(): ApiResult<RouteResponse> = call { api.route(LocalDate.now().toString()) }
    suspend fun stop(key: String): ApiResult<StopDetail> = call { api.stop(key) }
    suspend fun order(reference: String): ApiResult<OrderDetail> = call { api.order(reference) }

    // ---------- Status changes ----------
    suspend fun pickingUp(key: String): ApiResult<StatusResponse> {
        val body = action()
        return write("stops/$key/picking-up", body, "Picking Up $key") { api.pickingUp(key, body) }
    }

    suspend fun pickedUp(key: String, picked: List<String>, leftBehind: List<LeftBehind>, note: String?): ApiResult<PickedUpResponse> {
        val (lat, lng) = gps()
        val body = PickedUpRequest(picked, leftBehind, note?.ifBlank { null }, now(), lat, lng)
        return write("stops/$key/picked-up", body, "Picked Up $key") { api.pickedUp(key, body) }
    }

    suspend fun undo(key: String, expected: String): ApiResult<StatusResponse> = call { api.undo(key, UndoRequest(expected)) }

    suspend fun inTransit(key: String): ApiResult<StatusResponse> {
        val body = action()
        return write("stops/$key/in-transit", body, "In Transit $key") { api.inTransit(key, body) }
    }

    /**
     * Uploads one proof photo. Returns the server photo id, or "client:<uuid>" when there's no
     * signal (the file is queued and the server matches it by client_photo_id later).
     */
    suspend fun uploadPhoto(reference: String, file: File): ApiResult<String> {
        val clientId = UUID.randomUUID().toString()
        val (lat, lng) = gps()
        val takenAt = now()
        return try {
            val text = "text/plain".toMediaType()
            val part = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody("image/jpeg".toMediaType()))
            val res = api.uploadPhoto(
                part, reference.toRequestBody(text), clientId.toRequestBody(text), takenAt.toRequestBody(text),
                lat?.toString()?.toRequestBody(text), lng?.toString()?.toRequestBody(text),
            )
            ApiResult.Ok(res.photoId)
        } catch (e: HttpException) {
            errorFrom(e)
        } catch (e: IOException) {
            val form = buildMap {
                put("reference", reference); put("client_photo_id", clientId); put("taken_at", takenAt)
                lat?.let { put("latitude", it.toString()) }; lng?.let { put("longitude", it.toString()) }
            }
            queue.add(QueuedCall(kind = "photo", method = "POST", path = "photos", filePath = file.absolutePath, form = form, label = "Photo for $reference"))
            ApiResult.Ok("client:$clientId")
        }
    }

    suspend fun deletePhoto(photoId: String) {
        if (!photoId.startsWith("client:")) runCatching { api.deletePhoto(photoId) }
    }

    suspend fun delivered(key: String, reference: String, photos: List<String>, leftAt: String, contact: String, recipientName: String?): ApiResult<StatusResponse> {
        val (lat, lng) = gps()
        val body = DeliveredRequest(reference, photos, leftAt.trim(), contact, recipientName?.trim()?.ifBlank { null }, now(), lat, lng)
        return write("stops/$key/delivered", body, "Delivered $reference") { api.delivered(key, body) }
    }

    // ---------- Failed / problems ----------
    suspend fun failureReasons(): ApiResult<List<FailureReason>> = call { api.failureReasons() }

    suspend fun failed(key: String, reference: String, reason: String, note: String, nextStep: String): ApiResult<FailedResponse> {
        val (lat, lng) = gps()
        val body = FailedRequest(reference, reason, note.trim(), nextStep, emptyList(), now(), lat, lng)
        return write("stops/$key/failed", body, "Failed $reference") { api.failed(key, body) }
    }

    suspend fun reportProblem(reference: String, type: String, note: String) =
        call { api.reportException(ExceptionRequest(reference, type, note, now())) }

    // ---------- Messages ----------
    suspend fun messages(): ApiResult<MessagesResponse> = call { api.messages() }
    suspend fun markRead(id: Int) = call { api.markRead(id) }
    suspend fun star(id: Int, starred: Boolean) = call { api.star(id, StarRequest(starred)) }

    // ---------- Analytics / connectivity ----------
    suspend fun analytics(): ApiResult<AnalyticsSummary> = call { api.analytics("week") }
    suspend fun ping(): ApiResult<PingResponse> = call { api.ping() }
    suspend fun reportAppIssue(type: String, detail: String, device: Map<String, String>, net: Map<String, Boolean>) =
        call { api.appIssue(AppIssueRequest(type, detail, device, net)) }
}
