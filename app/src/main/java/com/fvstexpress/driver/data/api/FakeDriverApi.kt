package com.fvstexpress.driver.data.api

import com.fvstexpress.driver.data.model.AnalyticsSummary
import com.fvstexpress.driver.data.model.AppIssueRequest
import com.fvstexpress.driver.data.model.BreakDto
import com.fvstexpress.driver.data.model.ContactDto
import com.fvstexpress.driver.data.model.DayStats
import com.fvstexpress.driver.data.model.DeliveredRequest
import com.fvstexpress.driver.data.model.DriverDto
import com.fvstexpress.driver.data.model.ExceptionRequest
import com.fvstexpress.driver.data.model.FailedRequest
import com.fvstexpress.driver.data.model.FailedResponse
import com.fvstexpress.driver.data.model.FailureReason
import com.fvstexpress.driver.data.model.GeoPoint
import com.fvstexpress.driver.data.model.IdResponse
import com.fvstexpress.driver.data.model.Leg
import com.fvstexpress.driver.data.model.LoginRequest
import com.fvstexpress.driver.data.model.MessageDto
import com.fvstexpress.driver.data.model.MessagesResponse
import com.fvstexpress.driver.data.model.OrderDetail
import com.fvstexpress.driver.data.model.OrderRef
import com.fvstexpress.driver.data.model.PackageDto
import com.fvstexpress.driver.data.model.PersonDto
import com.fvstexpress.driver.data.model.PhotoResponse
import com.fvstexpress.driver.data.model.PickedUpOrder
import com.fvstexpress.driver.data.model.PickedUpRequest
import com.fvstexpress.driver.data.model.PickedUpResponse
import com.fvstexpress.driver.data.model.PingResponse
import com.fvstexpress.driver.data.model.PresenceRequest
import com.fvstexpress.driver.data.model.PresenceResponse
import com.fvstexpress.driver.data.model.ProofDto
import com.fvstexpress.driver.data.model.RefreshRequest
import com.fvstexpress.driver.data.model.RouteResponse
import com.fvstexpress.driver.data.model.StarRequest
import com.fvstexpress.driver.data.model.StatusActionRequest
import com.fvstexpress.driver.data.model.Status
import com.fvstexpress.driver.data.model.StatusResponse
import com.fvstexpress.driver.data.model.StopDay
import com.fvstexpress.driver.data.model.StopDetail
import com.fvstexpress.driver.data.model.StopOrder
import com.fvstexpress.driver.data.model.StopSummary
import com.fvstexpress.driver.data.model.StopsResponse
import com.fvstexpress.driver.data.model.TimeWindow
import com.fvstexpress.driver.data.model.TimelineEntry
import com.fvstexpress.driver.data.model.TokenResponse
import com.fvstexpress.driver.data.model.UndoRequest
import com.fvstexpress.driver.data.model.Waypoint
import com.fvstexpress.driver.data.model.WorkRequest
import com.fvstexpress.driver.data.model.WorkResponse
import com.fvstexpress.driver.data.model.WorkToday
import kotlinx.coroutines.delay
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

/**
 * In-memory stand-in for /driver-api/v1 with the same sample data as the mockup-driver pages
 * (Marcus Chen, Jam Florist pickup with 12 orders, JFSG-Grace-2 drop-off…). Used when
 * fvst.useMockApi=true so the app runs without a server. Any login + PIN works.
 */
class FakeDriverApi : DriverApi {

    private class FakeOrder(
        val reference: String,
        val name: String,
        var status: String,
        val packages: Int,
        val sender: String,
        val recipient: String,
        var dropoffKey: String? = null,
    )

    private class FakeStop(
        val key: String,
        val leg: String,
        val seq: Int,
        val dayOffset: Int,
        val address: String,
        val business: String,
        val contact: String,
        val contactPhone: String,
        val window: TimeWindow,
        val lat: Double,
        val lng: Double,
        val orders: List<FakeOrder>,
        val note: String? = null,
        val buzzer: String? = null,
        val urgent: Boolean = false,
        var status: String,
        var completedAt: String? = null,
        var proof: ProofDto? = null,
        val timeline: MutableList<TimelineEntry> = mutableListOf(),
        val history: ArrayDeque<String> = ArrayDeque(),
        val bannerMessageId: Int? = null,
    )

    private val driver = DriverDto(
        id = 412, name = "Marcus Chen", employeeCardNumber = "EMP-00412", vehicleType = "Cargo Van",
        phone = "+16045550110", online = true, dispatchPhone = "+16045550100",
    )
    private var online = true

    private val stops = mutableListOf<FakeStop>()
    private val messages = mutableListOf<MessageDto>()
    private var workStartedAt: String? = null
    private val breaks = mutableListOf<BreakDto>()

    init { seed() }

    private fun nowIso(): String = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    private fun hhmm(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

    private fun seed() {
        val senders = listOf("David Chen", "Lisa Wong", "Sarah Kim", "Tom Bennett", "Rachel Adams", "James Park", "Nina Patel", "Kevin Wu", "Amanda Lee", "Brian Ortiz", "Emily Garcia", "Michael Torres")
        val recipients = listOf("Grace Tan", "Grace Tan", "Owen Fraser", "Maya Chen", "Front desk", "Reception", "Cafe Lumen", "Front desk", "Kitchen Staff", "Print Shop Reception", "Front Counter", "Reception")
        val names = listOf("JFSG-Grace-1", "JFSG-Grace-2", "JFSG-Owen-1", "JFSG-Maya-1", "JFSG-Front-1", "JFSG-Reception-1", "JFSG-Cafe-1", "JFSG-Front-2", "JFSG-Kitchen-1", "JFSG-Reception-2", "JFSG-Front-3", "JFSG-Reception-3")
        val jamOrders = names.mapIndexed { i, n -> FakeOrder("JOB-${101 + i}", n, Status.ASSIGNED, if (i % 3 == 0) 1 else i % 4 + 1, senders[i], recipients[i]) }

        val grace2 = FakeOrder("JOB-90", "JFSG-Grace-2", Status.PICKED_UP, 2, "Lisa Wong", "Grace Tan")
        val owen = FakeOrder("JOB-91", "NSRS-Owen-1", Status.PICKED_UP, 1, "Jordan Wu", "Owen Fraser")
        val qbfs = FakeOrder("JOB-92", "QBFS-Front-2", Status.PICKED_UP, 3, "Maya Chen", "Front desk")
        val gvwPick = FakeOrder("JOB-93", "GVW-Reception-1", Status.ASSIGNED, 2, "Green Valley Wholesale", "Reception")
        val mbcPick = FakeOrder("JOB-94", "MBC-Cafe-1", Status.ASSIGNED, 1, "Sam Reyes", "Cafe Lumen")

        stops += FakeStop("P-1", Leg.PICKUP, 1, 0, "2140 West 41st Ave, Vancouver", "Jam Florist (South Granville)", "Mei Lin", "+16045550111",
            TimeWindow("08:00", "11:30"), 49.2346, -123.1559, jamOrders, note = "Ring buzzer #4, ask for Mei", buzzer = "#4",
            status = Status.ASSIGNED, bannerMessageId = 3).also { it.timeline += TimelineEntry(Status.ASSIGNED, "09:30") }
        stops += FakeStop("D-2", Leg.DROPOFF, 2, 0, "4488 Hazelbridge Way, Richmond", "", "Grace Tan", "+16045550122",
            TimeWindow("13:00", "13:30"), 49.1835, -123.1352, listOf(grace2), note = "Leave with concierge", buzzer = "1042", urgent = true,
            status = Status.PICKED_UP, bannerMessageId = 1).also { it.timeline += TimelineEntry(Status.PICKED_UP, "10:20") }
        stops += FakeStop("D-3", Leg.DROPOFF, 3, 0, "4680 No. 3 Rd, Richmond", "North Shore Restaurant Supply", "Owen Fraser", "+16045550133",
            TimeWindow("16:45", "17:15"), 49.1855, -123.1366, listOf(owen), note = "Gate code 7734 — call on arrival", status = Status.PICKED_UP, bannerMessageId = 2)
            .also { it.timeline += TimelineEntry(Status.PICKED_UP, "09:18") }
        stops += FakeStop("D-4", Leg.DROPOFF, 4, 0, "2985 Main St, Vancouver", "Queen Bee Flower Shop", "Front desk", "+16045550144",
            TimeWindow("14:00", "14:30"), 49.2591, -123.1007, listOf(qbfs), status = Status.PICKED_UP)
            .also { it.timeline += TimelineEntry(Status.PICKED_UP, "08:05") }
        stops += FakeStop("P-5", Leg.PICKUP, 5, 0, "1128 Homer St, Vancouver", "Green Valley Wholesale", "Reception", "+16045550155",
            TimeWindow("15:00", "16:00"), 49.2768, -123.1206, listOf(gvwPick), status = Status.ASSIGNED)
        stops += FakeStop("P-6", Leg.PICKUP, 1, 1, "3540 E Hastings St, Vancouver", "Maple Bakery Co.", "Sam Reyes", "+16045550166",
            TimeWindow("08:00", "10:00"), 49.2810, -123.0300, listOf(mbcPick), status = Status.ASSIGNED)

        val done1 = FakeOrder("JOB-80", "NSRS-Owen-1", Status.PICKED_UP, 1, "Jordan Wu", "Owen Fraser")
        val done2 = FakeOrder("JOB-81", "QBFS-Front-1", Status.DELIVERED, 1, "Maya Chen", "Front desk")
        stops += FakeStop("P-90", Leg.PICKUP, 0, 0, "1500 Main St, North Vancouver", "North Shore Restaurant Supply", "Jordan Wu", "+16045550177",
            TimeWindow("08:00", "09:30"), 49.3200, -123.0700, listOf(done1), status = Status.PICKED_UP, completedAt = "09:18")
            .also { it.timeline += listOf(TimelineEntry(Status.ASSIGNED, "08:50"), TimelineEntry(Status.PICKING_UP, "09:10"), TimelineEntry(Status.PICKED_UP, "09:18")) }
        stops += FakeStop("D-91", Leg.DROPOFF, 0, 0, "150 W 2nd St, North Vancouver", "Queen Bee Flower Shop", "Front desk", "+16045550188",
            TimeWindow("08:00", "09:00"), 49.3120, -123.0800, listOf(done2), status = Status.DELIVERED, completedAt = "08:47",
            proof = ProofDto("delivered", 1, "Front desk", "not_needed", null, completedAt = "08:47"))
            .also { it.timeline += listOf(TimelineEntry(Status.PICKED_UP, "08:05"), TimelineEntry(Status.IN_TRANSIT, "08:30"), TimelineEntry(Status.DELIVERED, "08:47")) }

        jamOrders[0].dropoffKey = "D-2"
        messages += MessageDto(1, "JOB-90", "JFSG-Grace-2", Leg.DROPOFF, "D-2", "Delivery window moved to 1:00–1:30 PM",
            "Customer requested a later window — dispatch approved. No other changes to this stop.", nowIso(), read = false)
        messages += MessageDto(2, "JOB-91", "NSRS-Owen-1", Leg.DROPOFF, "D-3", "Recipient added a gate code",
            "Gate code 7734. Recipient asked that you call on arrival, not just buzz.", nowIso(), read = false)
        messages += MessageDto(3, "JOB-101", "JFSG-Grace-1 & JFSG-Grace-2", Leg.PICKUP, "P-1", "Pickup ready 15 min early",
            "Jam Florist says JFSG-Grace-1 & JFSG-Grace-2 are packed and ready now, ahead of the 8:00 AM window. The other 10 orders at this stop aren't affected.", nowIso(), read = true)
        messages += MessageDto(4, "JOB-92", "QBFS-Front-2", Leg.DROPOFF, "D-4", "Address confirmed correct",
            "Customer confirmed 2985 Main St is correct — no unit number, front desk will accept.", nowIso(), read = true)
        workStartedAt = "09:18"
    }

    private suspend fun lag() = delay(250)

    private fun notFound(): Nothing =
        throw HttpException(Response.error<Any>(404, """{"error":"Not found."}""".toResponseBody()))

    private fun conflict(msg: String): Nothing =
        throw HttpException(Response.error<Any>(409, """{"error":"$msg"}""".toResponseBody()))

    private fun stopOf(key: String) = stops.firstOrNull { it.key == key } ?: notFound()

    private fun isCompleted(s: FakeStop) = s.completedAt != null

    private fun summary(s: FakeStop) = StopSummary(
        stopKey = s.key, leg = s.leg, seq = s.seq, urgent = s.urgent, status = s.status, address = s.address,
        buzzer = s.buzzer, business = s.business.ifBlank { null }, contact = s.contact, window = s.window,
        deadlineLabel = if (s.leg == Leg.PICKUP) "By ${s.window.end}" else null,
        orders = s.orders.map { OrderRef(it.reference, it.name) }, completedAt = s.completedAt,
    )

    private fun nextAllowed(s: FakeStop): List<String> = when {
        isCompleted(s) -> emptyList()
        s.leg == Leg.PICKUP && s.status == Status.ASSIGNED -> listOf(Status.PICKING_UP)
        s.leg == Leg.PICKUP && s.status == Status.PICKING_UP -> listOf(Status.PICKED_UP)
        s.leg == Leg.DROPOFF && s.status == Status.PICKED_UP -> listOf(Status.IN_TRANSIT, Status.DELIVERED)
        s.leg == Leg.DROPOFF && s.status == Status.IN_TRANSIT -> listOf(Status.DELIVERED)
        else -> emptyList()
    }

    private fun move(s: FakeStop, to: String, completed: Boolean = false) {
        s.history.addLast(s.status)
        s.status = to
        s.orders.forEach { if (it.status != Status.FAILED) it.status = to }
        s.timeline += TimelineEntry(to, hhmm())
        if (completed) s.completedAt = hhmm()
    }

    // ---------------- Auth ----------------
    override suspend fun login(body: LoginRequest): TokenResponse {
        lag()
        if (body.login.isBlank() || body.pin.isBlank()) {
            throw HttpException(Response.error<Any>(422, """{"errors":{"login":"Required."}}""".toResponseBody()))
        }
        return TokenResponse("fake-access-" + UUID.randomUUID(), "fake-refresh-" + UUID.randomUUID(), 900, driver.copy(online = online))
    }

    override suspend fun refresh(body: RefreshRequest) = TokenResponse("fake-access-" + UUID.randomUUID(), body.refreshToken, 900, null)
    override suspend fun logout(body: RefreshRequest): Response<Unit> = Response.success(Unit)

    // ---------------- Driver ----------------
    override suspend fun me(): DriverDto { lag(); return driver.copy(online = online) }

    override suspend fun presence(body: PresenceRequest): PresenceResponse { online = body.online; return PresenceResponse(online) }

    override suspend fun work(body: WorkRequest): WorkResponse {
        lag()
        when (body.event) {
            "start" -> workStartedAt = hhmm()
            "break_start" -> { if (breaks.lastOrNull()?.end == null && breaks.isNotEmpty()) conflict("Already on a break."); breaks += BreakDto(hhmm()) }
            "break_end" -> { val last = breaks.lastOrNull() ?: conflict("Not on a break."); breaks[breaks.lastIndex] = last.copy(end = hhmm()) }
        }
        return WorkResponse(1, workedMinutes())
    }

    private fun workedMinutes(): Int {
        val start = workStartedAt?.let { LocalTime.parse(it) } ?: return 0
        val now = LocalTime.now()
        var mins = java.time.Duration.between(start, now).toMinutes().toInt().coerceAtLeast(0)
        breaks.forEach { b ->
            val bs = LocalTime.parse(b.start)
            val be = b.end?.let { LocalTime.parse(it) } ?: now
            mins -= java.time.Duration.between(bs, be).toMinutes().toInt().coerceAtLeast(0)
        }
        return mins.coerceAtLeast(0)
    }

    override suspend fun workToday(): WorkToday {
        lag()
        return WorkToday(workStartedAt, workedMinutes(), breaks.lastOrNull()?.let { it.end == null } ?: false, breaks.toList())
    }

    // ---------------- Tasks ----------------
    override suspend fun stops(view: String): StopsResponse {
        lag()
        val today = LocalDate.now()
        fun dayLabel(offset: Int): String {
            val d = today.plusDays(offset.toLong())
            val dow = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CANADA)
            val mon = d.month.getDisplayName(TextStyle.SHORT, Locale.CANADA)
            return when (offset) { 0 -> "Today — $dow, $mon ${d.dayOfMonth}"; 1 -> "Tomorrow — $dow, $mon ${d.dayOfMonth}"; else -> "$dow, $mon ${d.dayOfMonth}" }
        }
        val selected = when (view) {
            "completed" -> stops.filter { isCompleted(it) }
            "today" -> stops.filter { !isCompleted(it) && it.dayOffset == 0 }
            else -> stops.filter { !isCompleted(it) }
        }
        return StopsResponse(
            selected.groupBy { it.dayOffset }.toSortedMap().map { (offset, list) ->
                StopDay(today.plusDays(offset.toLong()).toString(), dayLabel(offset), list.sortedBy { if (view == "completed") 0 else it.seq }.map { summary(it) })
            },
        )
    }

    override suspend fun route(date: String): RouteResponse {
        lag()
        val todays = stops.filter { it.dayOffset == 0 }.sortedBy { it.seq }
        return RouteResponse(
            driverPosition = GeoPoint(49.2450, -123.1150, nowIso()),
            waypoints = todays.mapIndexed { i, s -> Waypoint(i + 1, s.key, s.leg, isCompleted(s), s.urgent, s.lat, s.lng, s.orders.size) },
        )
    }

    override suspend fun stop(stopKey: String): StopDetail {
        lag()
        val s = stopOf(stopKey)
        val contacts = if (s.leg == Leg.PICKUP) listOf(ContactDto("pickup_contact", s.contact, s.contactPhone))
        else listOf(ContactDto("recipient", s.contact, s.contactPhone), ContactDto("sender", s.orders.first().sender, "+16045550199"))
        return StopDetail(
            stopKey = s.key, leg = s.leg, status = s.status, urgent = s.urgent, address = s.address, buzzer = s.buzzer,
            business = s.business.ifBlank { null }, window = s.window, note = s.note, contacts = contacts,
            orders = s.orders.map { StopOrder(it.reference, it.name, it.status, it.packages, it.sender, it.recipient, it.dropoffKey, "+16045550199", "+16045550122") },
            timeline = s.timeline.toList(), banner = s.bannerMessageId?.let { id -> messages.firstOrNull { it.id == id } },
            proof = s.proof, nextAllowed = nextAllowed(s), canUndo = s.history.isNotEmpty() && s.status != Status.DELIVERED,
            latitude = s.lat, longitude = s.lng,
        )
    }

    override suspend fun order(reference: String): OrderDetail {
        lag()
        val (stop, o) = stops.firstNotNullOfOrNull { st -> st.orders.firstOrNull { it.reference == reference }?.let { st to it } } ?: notFound()
        return OrderDetail(
            o.reference, o.name, o.packages, listOf(PackageDto(o.packages, "Medium", "2.5")),
            PersonDto(o.sender, "+16045550199"), PersonDto(o.recipient, "+16045550122", if (stop.leg == Leg.DROPOFF) stop.address else null), stop.note,
        )
    }

    // ---------------- Status changes ----------------
    override suspend fun pickingUp(stopKey: String, body: StatusActionRequest): StatusResponse {
        lag()
        val s = stopOf(stopKey)
        if (s.status != Status.ASSIGNED) conflict("This stop is already ${Status.label(s.status)}.")
        move(s, Status.PICKING_UP)
        return StatusResponse(s.status, canUndo = true)
    }

    override suspend fun pickedUp(stopKey: String, body: PickedUpRequest): PickedUpResponse {
        lag()
        val s = stopOf(stopKey)
        if (s.status != Status.PICKING_UP) conflict("Mark Picking Up first.")
        if (body.pickedUp.isEmpty()) throw HttpException(Response.error<Any>(422, """{"errors":{"picked_up":"Pick at least one order."}}""".toResponseBody()))
        body.leftBehind.forEach { lb -> s.orders.firstOrNull { it.reference == lb.reference }?.status = if (lb.action == "fail") Status.FAILED else Status.ASSIGNED }
        s.history.addLast(s.status)
        s.status = Status.PICKED_UP
        s.orders.filter { it.reference in body.pickedUp }.forEach { it.status = Status.PICKED_UP }
        s.timeline += TimelineEntry(Status.PICKED_UP, hhmm())
        s.completedAt = hhmm()
        if (workStartedAt == null) workStartedAt = hhmm()
        return PickedUpResponse(
            pickedUp = s.orders.filter { it.reference in body.pickedUp }.map { PickedUpOrder(it.reference, it.dropoffKey) },
            restarted = body.leftBehind.filter { it.action == "restart" }.map { it.reference },
            failed = body.leftBehind.filter { it.action == "fail" }.map { it.reference },
        )
    }

    override suspend fun undo(stopKey: String, body: UndoRequest): StatusResponse {
        lag()
        val s = stopOf(stopKey)
        if (s.status != body.expectedStatus) conflict("The stop changed — reload it.")
        val prev = s.history.removeLastOrNull() ?: conflict("Nothing to undo.")
        s.status = prev
        s.orders.forEach { it.status = prev }
        s.timeline.removeLastOrNull()
        s.completedAt = null
        s.proof = null
        return StatusResponse(prev, canUndo = s.history.isNotEmpty())
    }

    override suspend fun inTransit(stopKey: String, body: StatusActionRequest): StatusResponse {
        lag()
        val s = stopOf(stopKey)
        if (s.status != Status.PICKED_UP) conflict("This stop is ${Status.label(s.status)}.")
        move(s, Status.IN_TRANSIT)
        return StatusResponse(s.status, canUndo = true)
    }

    override suspend fun uploadPhoto(
        file: MultipartBody.Part, reference: RequestBody, clientPhotoId: RequestBody, takenAt: RequestBody,
        latitude: RequestBody?, longitude: RequestBody?,
    ): PhotoResponse { delay(400); return PhotoResponse("pho_" + UUID.randomUUID().toString().take(8), "pod/demo.jpg") }

    override suspend fun deletePhoto(photoId: String): Response<Unit> = Response.success(Unit)

    override suspend fun delivered(stopKey: String, body: DeliveredRequest): StatusResponse {
        lag()
        val s = stopOf(stopKey)
        if (s.status != Status.PICKED_UP && s.status != Status.IN_TRANSIT) conflict("This stop is ${Status.label(s.status)}.")
        if (body.photos.isEmpty()) throw HttpException(Response.error<Any>(422, """{"errors":{"photos":"Take at least 1 photo."}}""".toResponseBody()))
        if (s.status == Status.PICKED_UP) move(s, Status.IN_TRANSIT)
        move(s, Status.DELIVERED, completed = true)
        s.proof = ProofDto("delivered", body.photos.size, body.leftAt, body.customerContact, body.recipientName, completedAt = s.completedAt)
        return StatusResponse(Status.DELIVERED, canUndo = false, completedAt = s.completedAt)
    }

    override suspend fun failureReasons(): List<FailureReason> = listOf(
        FailureReason("customer_not_present", "Customer Not Present"),
        FailureReason("incorrect_address", "Incorrect Address"),
        FailureReason("last_minute_address_change", "Last-Minute Address Change"),
        FailureReason("unable_to_contact", "Unable to Contact Customer"),
        FailureReason("office_closed", "Office Closed"),
        FailureReason("damaged_package", "Damaged Package"),
        FailureReason("incorrect_date", "Incorrect Date"),
        FailureReason("driver_delay", "Driver Delay"),
        FailureReason("order_cancellation", "Order Cancellation"),
        FailureReason("other", "Other", noteRequired = true),
    )

    override suspend fun failed(stopKey: String, body: FailedRequest): FailedResponse {
        lag()
        val s = stopOf(stopKey)
        if (body.nextStep == "restart") {
            s.timeline += TimelineEntry("restarted", hhmm())
            return FailedResponse(s.status, body.nextStep, 219)
        }
        move(s, Status.FAILED, completed = true)
        s.proof = ProofDto("failed", body.photos.size, failureReason = body.reason, note = body.note, completedAt = s.completedAt)
        return FailedResponse(Status.FAILED, body.nextStep, 219)
    }

    override suspend fun reportException(body: ExceptionRequest) = IdResponse(220)

    // ---------------- Messages ----------------
    override suspend fun messages(): MessagesResponse { lag(); return MessagesResponse(messages.count { !it.read }, messages.toList()) }

    override suspend fun markRead(id: Int): Response<Unit> {
        val i = messages.indexOfFirst { it.id == id }
        if (i >= 0) messages[i] = messages[i].copy(read = true)
        return Response.success(Unit)
    }

    override suspend fun star(id: Int, body: StarRequest): StarRequest {
        val i = messages.indexOfFirst { it.id == id }
        if (i >= 0) messages[i] = messages[i].copy(starred = body.starred)
        return body
    }

    // ---------------- Analytics & connectivity ----------------
    override suspend fun analytics(range: String): AnalyticsSummary {
        lag()
        val today = LocalDate.now()
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val sample = listOf(Triple(9, 100, 8.0), Triple(7, 95, 7.5), Triple(8, 94, 8.2), Triple(10, 96, 8.4), Triple(4, 100, 4.2), Triple(0, 0, 0.0), Triple(0, 0, 0.0))
        val delivered = stops.count { it.status == Status.DELIVERED }
        val days = (0..(today.dayOfWeek.value - 1)).map { i ->
            val d = monday.plusDays(i.toLong())
            val (st, pct, h) = sample[i]
            DayStats(d.toString(), d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CANADA), if (d == today) delivered else st, pct, if (d == today) workedMinutes() / 60.0 else h, d == today)
        }
        return AnalyticsSummary(com.fvstexpress.driver.data.model.TodayStats(delivered, workedMinutes() / 60.0), days)
    }

    override suspend fun ping() = PingResponse("demo", nowIso(), "1.0.0")
    override suspend fun appIssue(body: AppIssueRequest) = IdResponse(44)
}
