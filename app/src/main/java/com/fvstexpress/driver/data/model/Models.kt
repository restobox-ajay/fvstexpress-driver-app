package com.fvstexpress.driver.data.model

import kotlinx.serialization.Serializable

/*
 * Wire models for /driver-api/v1. Property names are camelCase here and snake_case on the wire
 * (see ApiFactory.json: JsonNamingStrategy.SnakeCase), e.g. stopKey <-> "stop_key".
 */

// ---------- Auth ----------
@Serializable
data class LoginRequest(val login: String, val pin: String, val deviceId: String, val pushToken: String? = null)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int = 900,
    val driver: DriverDto? = null,
)

// ---------- Driver / presence / hours ----------
@Serializable
data class DriverDto(
    val id: Int,
    val name: String,
    val employeeCardNumber: String? = null,
    val vehicleType: String? = null,
    val phone: String? = null,
    val online: Boolean = false,
    val forcedOffline: Boolean = false,
    val dispatchPhone: String? = null,
)

@Serializable
data class PresenceRequest(val online: Boolean, val latitude: Double? = null, val longitude: Double? = null)

@Serializable
data class PresenceResponse(val online: Boolean)

/** event = start | break_start | break_end | end */
@Serializable
data class WorkRequest(val event: String, val at: String? = null)

@Serializable
data class WorkResponse(val session: Int? = null, val minutes: Int? = null)

@Serializable
data class WorkToday(
    val startedAt: String? = null,
    val workedMinutes: Int = 0,
    val onBreak: Boolean = false,
    val breaks: List<BreakDto> = emptyList(),
)

@Serializable
data class BreakDto(val start: String, val end: String? = null)

// ---------- Stops / route ----------
object Leg {
    const val PICKUP = "pickup"
    const val DROPOFF = "dropoff"
}

/** Values of App\Enum\OrderStatus on the server. */
object Status {
    const val ASSIGNED = "assigned"
    const val PICKING_UP = "picking_up"
    const val PICKED_UP = "picked_up"
    const val IN_TRANSIT = "in_transit"
    const val DELIVERED = "delivered"
    const val FAILED = "failed"
    const val CANCELLED = "cancelled"

    fun label(value: String): String = when (value) {
        "received" -> "Order Received"
        "pending" -> "Pending"
        ASSIGNED -> "Assigned"
        PICKING_UP -> "Picking Up"
        PICKED_UP -> "Picked Up"
        IN_TRANSIT -> "In Transit"
        DELIVERED -> "Delivered"
        FAILED -> "Failed"
        CANCELLED -> "Cancelled"
        else -> value
    }
}

@Serializable
data class StopsResponse(val days: List<StopDay> = emptyList())

@Serializable
data class StopDay(val date: String, val label: String, val stops: List<StopSummary> = emptyList())

@Serializable
data class StopSummary(
    val stopKey: String,
    val leg: String,
    val seq: Int = 0,
    val urgent: Boolean = false,
    val status: String,
    val address: String,
    val buzzer: String? = null,
    val business: String? = null,
    val contact: String? = null,
    val window: TimeWindow? = null,
    val deadlineLabel: String? = null,
    val orders: List<OrderRef> = emptyList(),
    val completedAt: String? = null,
)

@Serializable
data class TimeWindow(val start: String? = null, val end: String? = null)

@Serializable
data class OrderRef(val reference: String, val name: String)

@Serializable
data class RouteResponse(val driverPosition: GeoPoint? = null, val waypoints: List<Waypoint> = emptyList())

@Serializable
data class GeoPoint(val latitude: Double, val longitude: Double, val at: String? = null)

@Serializable
data class Waypoint(
    val seq: Int,
    val stopKey: String,
    val leg: String,
    val done: Boolean = false,
    val urgent: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val orderCount: Int = 1,
    val address: String? = null,
)

@Serializable
data class StopDetail(
    val stopKey: String,
    val leg: String,
    val status: String,
    val urgent: Boolean = false,
    val address: String,
    val buzzer: String? = null,
    val business: String? = null,
    val window: TimeWindow? = null,
    val note: String? = null,
    val noResponseInstructions: String? = null,
    val contacts: List<ContactDto> = emptyList(),
    val orders: List<StopOrder> = emptyList(),
    val timeline: List<TimelineEntry> = emptyList(),
    val banner: MessageDto? = null,
    val proof: ProofDto? = null,
    val nextAllowed: List<String> = emptyList(),
    val canUndo: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/** role = pickup_contact | recipient | sender */
@Serializable
data class ContactDto(val role: String, val name: String, val phone: String? = null)

@Serializable
data class StopOrder(
    val reference: String,
    val name: String,
    val status: String,
    val packageCount: Int = 1,
    val sender: String? = null,
    val recipient: String? = null,
    val dropoffStopKey: String? = null,
    val senderPhone: String? = null,
    val recipientPhone: String? = null,
)

@Serializable
data class TimelineEntry(val status: String, val at: String)

@Serializable
data class ProofDto(
    val outcome: String,
    val photoCount: Int = 0,
    val leftAt: String? = null,
    val customerContact: String? = null,
    val recipientName: String? = null,
    val failureReason: String? = null,
    val note: String? = null,
    val completedAt: String? = null,
)

@Serializable
data class OrderDetail(
    val reference: String,
    val name: String,
    val packageCount: Int = 1,
    val packages: List<PackageDto> = emptyList(),
    val sender: PersonDto? = null,
    val recipient: PersonDto? = null,
    val pickupNote: String? = null,
)

@Serializable
data class PackageDto(val quantity: Int, val size: String? = null, val weightKg: String? = null)

@Serializable
data class PersonDto(val name: String, val phone: String? = null, val address: String? = null)

// ---------- Status changes ----------
@Serializable
data class StatusActionRequest(val clientTime: String, val latitude: Double? = null, val longitude: Double? = null)

@Serializable
data class StatusResponse(val status: String, val canUndo: Boolean = false, val completedAt: String? = null)

/** action = restart | fail */
@Serializable
data class LeftBehind(val reference: String, val action: String)

@Serializable
data class PickedUpRequest(
    val pickedUp: List<String>,
    val leftBehind: List<LeftBehind> = emptyList(),
    val note: String? = null,
    val clientTime: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class PickedUpOrder(val reference: String, val dropoffStopKey: String? = null)

@Serializable
data class PickedUpResponse(
    val pickedUp: List<PickedUpOrder> = emptyList(),
    val restarted: List<String> = emptyList(),
    val failed: List<String> = emptyList(),
    val canUndo: Boolean = true,
)

@Serializable
data class UndoRequest(val expectedStatus: String)

@Serializable
data class PhotoResponse(val photoId: String, val path: String? = null)

/** customerContact = not_needed | called | texted */
@Serializable
data class DeliveredRequest(
    val reference: String,
    val photos: List<String>,
    val leftAt: String,
    val customerContact: String,
    val recipientName: String? = null,
    val clientTime: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/** nextStep = restart | return_to_pool */
@Serializable
data class FailedRequest(
    val reference: String,
    val reason: String,
    val note: String,
    val nextStep: String,
    val photos: List<String> = emptyList(),
    val clientTime: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class FailedResponse(val status: String, val nextStep: String, val exceptionId: Int? = null)

@Serializable
data class FailureReason(val code: String, val label: String, val noteRequired: Boolean = false)

@Serializable
data class ExceptionRequest(val orderRef: String, val type: String, val note: String? = null, val occurredAt: String? = null)

@Serializable
data class IdResponse(val id: Int)

// ---------- Messages ----------
@Serializable
data class MessagesResponse(val unread: Int = 0, val messages: List<MessageDto> = emptyList())

@Serializable
data class MessageDto(
    val id: Int,
    val reference: String? = null,
    val orderName: String? = null,
    val leg: String? = null,
    val stopKey: String? = null,
    val subject: String,
    val body: String,
    val sentAt: String,
    val read: Boolean = false,
    val starred: Boolean = false,
)

@Serializable
data class StarRequest(val starred: Boolean)

// ---------- Analytics / connectivity ----------
@Serializable
data class AnalyticsSummary(val today: TodayStats, val daily: List<DayStats> = emptyList())

@Serializable
data class TodayStats(val deliveries: Int = 0, val hours: Double = 0.0)

@Serializable
data class DayStats(
    val date: String,
    val label: String,
    val stops: Int = 0,
    val onTimePct: Int = 0,
    val hours: Double = 0.0,
    val today: Boolean = false,
)

@Serializable
data class PingResponse(val server: String = "", val time: String = "", val minAppVersion: String? = null)

/** type = connectivity | app_function | camera | other */
@Serializable
data class AppIssueRequest(
    val type: String,
    val detail: String,
    val device: Map<String, String> = emptyMap(),
    val netStatus: Map<String, Boolean> = emptyMap(),
)
