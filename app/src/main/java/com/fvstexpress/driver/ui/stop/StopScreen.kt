package com.fvstexpress.driver.ui.stop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.fvstexpress.driver.AppContainer
import com.fvstexpress.driver.R
import com.fvstexpress.driver.data.model.ContactDto
import com.fvstexpress.driver.data.model.Leg
import com.fvstexpress.driver.data.model.Status
import com.fvstexpress.driver.data.model.StopDetail
import com.fvstexpress.driver.data.model.StopOrder
import com.fvstexpress.driver.ui.components.AppShell
import com.fvstexpress.driver.ui.components.BottomTab
import com.fvstexpress.driver.ui.components.BtnKind
import com.fvstexpress.driver.ui.components.ConfirmDialog
import com.fvstexpress.driver.ui.components.ContactDot
import com.fvstexpress.driver.ui.components.ErrorState
import com.fvstexpress.driver.ui.components.FormGroup
import com.fvstexpress.driver.ui.components.FormLabel
import com.fvstexpress.driver.ui.components.FvBadge
import com.fvstexpress.driver.ui.components.FvButton
import com.fvstexpress.driver.ui.components.FvIcon
import com.fvstexpress.driver.ui.components.FvInput
import com.fvstexpress.driver.ui.components.FvModal
import com.fvstexpress.driver.ui.components.H1
import com.fvstexpress.driver.ui.components.H2
import com.fvstexpress.driver.ui.components.H3
import com.fvstexpress.driver.ui.components.InfoRow
import com.fvstexpress.driver.ui.components.LegTag
import com.fvstexpress.driver.ui.components.Loading
import com.fvstexpress.driver.ui.components.ModalActions
import com.fvstexpress.driver.ui.components.OfflineNote
import com.fvstexpress.driver.ui.components.OnlineToggle
import com.fvstexpress.driver.ui.components.OrderMessage
import com.fvstexpress.driver.ui.components.RoundIconButton
import com.fvstexpress.driver.ui.components.Rule
import com.fvstexpress.driver.ui.components.Step
import com.fvstexpress.driver.ui.components.StatusStepper
import com.fvstexpress.driver.ui.components.T
import com.fvstexpress.driver.ui.components.ToggleGroup
import com.fvstexpress.driver.ui.components.Tone
import com.fvstexpress.driver.ui.components.TopBar
import com.fvstexpress.driver.ui.components.TopIcon
import com.fvstexpress.driver.ui.components.UrgencyFlag
import com.fvstexpress.driver.ui.components.rememberOnlineToggle
import com.fvstexpress.driver.ui.nav.Routes
import com.fvstexpress.driver.ui.theme.Fv
import com.fvstexpress.driver.ui.util.dial
import com.fvstexpress.driver.ui.util.navigateTo
import com.fvstexpress.driver.ui.util.text
import com.fvstexpress.driver.ui.util.timeLabel
import com.fvstexpress.driver.ui.util.windowLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * task-detail-pickup.html / task-detail-delivery.html / *-done.html — one screen whose content
 * follows the stop's status. Photos are taken on the in-app camera (task-camera.html).
 */
@Composable
fun StopScreen(nav: NavController, container: AppContainer, stopKey: String) {
    val vm: StopViewModel = viewModel(key = "stop-$stopKey") { StopViewModel(container.repository, stopKey) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val (online, setOnline) = rememberOnlineToggle(container)

    // A photo taken on the camera screen comes back through the saved state handle.
    val captured = nav.currentBackStackEntry?.savedStateHandle?.getStateFlow<String?>("captured_photo", null)?.collectAsStateWithLifecycle()
    LaunchedEffect(captured?.value) {
        captured?.value?.let { path ->
            vm.addPhoto(java.io.File(path))
            nav.currentBackStackEntry?.savedStateHandle?.set("captured_photo", null)
        }
    }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }

    AppShell(
        nav = nav, container = container, tab = BottomTab.TASKS, snackbar = snackbar,
        topBar = {
            TopBar(
                left = { TopIcon(R.drawable.ic_arrow_left, "Back") { nav.popBackStack() } },
                center = { OnlineToggle(online, setOnline) },
            )
        },
    ) { modifier ->
        val d = state.detail
        when {
            state.loading -> Loading(modifier)
            d == null -> Box(modifier) { ErrorState(state.error ?: "Couldn't load this stop.") { vm.load() } }
            state.isCompleted -> Done(d, state, nav, modifier)
            else -> Active(d, state, vm, nav, modifier)
        }
    }

    state.orderInfo?.let { o ->
        val checked = o.reference in state.checked
        FvModal(vm::hideOrder, o.name) {
            Column(Modifier.padding(bottom = 16.dp)) {
                val sizes = o.packages.flatMap { p -> List(p.quantity.coerceAtLeast(1)) { p.size ?: "Item" } }
                sizes.forEachIndexed { i, size -> OrderInfoRow(if (sizes.size == 1) "Item size" else "Item ${i + 1} size", size) }
            }
            Column(Modifier.padding(bottom = 16.dp)) {
                OrderInfoRow("Sender", o.sender?.name ?: "—")
                OrderInfoRow("Recipient", o.recipient?.name ?: "—")
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FvButton("Close", vm::hideOrder, Modifier.fillMaxWidth())
                if (state.detail?.status == Status.PICKING_UP) {
                    FvButton(if (checked) "Unmark Picked Up" else "Mark Picked Up", { vm.toggleChecked(o.reference); vm.hideOrder() }, Modifier.fillMaxWidth(), BtnKind.Primary)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Active stop
// ---------------------------------------------------------------------------

@Composable
private fun Active(d: StopDetail, state: StopState, vm: StopViewModel, nav: NavController, modifier: Modifier) {
    val context = LocalContext.current
    val isPickup = d.leg == Leg.PICKUP
    var confirmTarget by remember { mutableStateOf<String?>(null) }
    var leftBehindOpen by remember { mutableStateOf(false) }
    var ordersOpen by remember { mutableStateOf(false) }
    var contactPicker by remember { mutableStateOf<String?>(null) }  // "call" | "text"
    var personSheet by remember { mutableStateOf<ContactDto?>(null) }

    Column(modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
        // Header (.detail-header content)
        HeaderTitle(d)

        // Order line
        val names = d.orders.map { it.name }
        val orderLine = when {
            names.size <= 2 -> names.joinToString(" & ")
            else -> names.take(2).joinToString(" & ") + ", +${names.size - 2} orders"
        }
        T(orderLine, 13.sp, modifier = Modifier.padding(top = 12.dp).clickable(enabled = names.size > 1) { ordersOpen = true })

        if (isPickup) {
            val pc = d.contacts.firstOrNull()
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                T(listOfNotNull(d.business, pc?.name).joinToString(", "), 13.sp)
                if (pc != null) ContactDot { personSheet = pc }
            }
        } else {
            val recipient = d.contacts.firstOrNull { it.role == "recipient" }
            val sender = d.contacts.firstOrNull { it.role == "sender" }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                recipient?.let { T("Recipient: ${it.name}", 13.sp); ContactDot { personSheet = it } }
                Spacer(Modifier.width(14.dp))
                sender?.let { T("Sender: ${it.name}", 13.sp); ContactDot { personSheet = it } }
            }
        }
        T(windowLabel(d.window), 13.sp, modifier = Modifier.padding(top = 10.dp))
        val note = d.note ?: d.noResponseInstructions
        if (note != null) {
            Row(Modifier.padding(top = 10.dp)) {
                T("Note:", 13.sp, Fv.TextFaint)
                Spacer(Modifier.width(10.dp))
                T(note, 13.sp)
            }
        }
        d.banner?.let { b ->
            OrderMessage(buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Fv.Danger)) { append(b.subject) }
                append(" — ${b.body}")
            }, Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(10.dp))
        Rule()

        // Status stepper + actions
        val steps = if (isPickup) listOf(Status.ASSIGNED, Status.PICKING_UP, Status.PICKED_UP) else listOf(Status.PICKED_UP, Status.IN_TRANSIT, Status.DELIVERED)
        val times = d.timeline.associate { it.status to timeLabel(it.at) }
        val idx = steps.indexOf(d.status).coerceAtLeast(0)
        val currentIsInProgress = (isPickup && d.status == Status.PICKING_UP) || (!isPickup && d.status == Status.IN_TRANSIT)
        Box(Modifier.padding(vertical = 10.dp)) {
            StatusStepper(
                steps.map { Step(Status.label(it), times[it]) },
                doneCount = if (currentIsInProgress) idx else idx + 1,
                currentIndex = if (currentIsInProgress) idx else null,
            )
        }

        if (isPickup && d.status == Status.PICKING_UP) {
            Rule()
            Checklist(d.orders, state.checked, onToggle = vm::toggleChecked, onInfo = vm::showOrder)
        }

        val next = d.nextAllowed.firstOrNull()
        if (isPickup || d.status == Status.PICKED_UP) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (d.canUndo) FvButton("Undo", vm::undo, Modifier.weight(0.25f), enabled = !state.busy)
                if (isPickup && d.status == Status.PICKING_UP) {
                    val picked = d.orders.filter { it.reference in state.checked }
                    val items = picked.sumOf { it.packageCount }
                    val label = if (picked.isEmpty()) "Mark Picked Up"
                    else "Mark Picked Up ${picked.size} order${if (picked.size == 1) "" else "s"}, $items item${if (items == 1) "" else "s"}"
                    FvButton(label, {
                        if (d.orders.all { it.reference in state.checked }) confirmTarget = Status.PICKED_UP else leftBehindOpen = true
                    }, Modifier.weight(0.75f), BtnKind.Primary, enabled = picked.isNotEmpty(), busy = state.busy)
                } else if (next != null) {
                    FvButton("Mark ${Status.label(next)}", { confirmTarget = next }, Modifier.weight(0.75f), BtnKind.Primary, busy = state.busy)
                }
            }
        } else if (d.canUndo) {
            FvButton("Undo", vm::undo, Modifier.padding(top = 12.dp), enabled = !state.busy)
        }
        Spacer(Modifier.height(10.dp))
        Rule()

        if (!isPickup) {
            if (d.status == Status.IN_TRANSIT) ProofOfDeliveryForm(state, vm, nav, d)
            FvButton(
                "Mark Delivered", { confirmTarget = Status.DELIVERED }, Modifier.fillMaxWidth().padding(top = 12.dp), BtnKind.Primary,
                enabled = d.status == Status.IN_TRANSIT && state.canDeliver, busy = state.busy && confirmTarget == null,
            )
            FvButton(
                "Delivery failed / report a problem", { nav.navigate(Routes.failed(d.stopKey, d.orders.first().reference)) },
                Modifier.fillMaxWidth().padding(top = 8.dp), BtnKind.Danger,
            )
        }

        // Contact (.info-card)
        Column(Modifier.padding(vertical = 10.dp)) {
            H2("Contact", Modifier.padding(bottom = 8.dp))
            val recipient = d.contacts.firstOrNull { it.role == "recipient" }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FvButton("Call", { if (isPickup) contactPicker = "call" else context.dial(recipient?.phone) }, Modifier.weight(1f), icon = R.drawable.ic_phone)
                FvButton("Text", { if (isPickup) contactPicker = "text" else context.text(recipient?.phone) }, Modifier.weight(1f), icon = R.drawable.ic_chat)
            }
        }
        Rule()
        if (isPickup) {
            FvButton("Go to Maps App", { context.navigateTo(d.address) }, Modifier.fillMaxWidth().padding(top = 10.dp), icon = R.drawable.ic_map)
            FvButton(
                "Report a problem", { nav.navigate(Routes.failed(d.stopKey, d.orders.first().reference)) },
                Modifier.fillMaxWidth().padding(top = 8.dp), BtnKind.Danger,
            )
        }
        Spacer(Modifier.height(20.dp))
    }

    // ----- popups -----
    confirmTarget?.let { target ->
        ConfirmDialog(
            "Confirm status change", "Please confirm order change to ${Status.label(target)}?",
            onConfirm = {
                confirmTarget = null
                when (target) {
                    Status.PICKING_UP -> vm.markPickingUp()
                    Status.PICKED_UP -> vm.markPickedUp(emptyMap(), "")
                    Status.IN_TRANSIT -> vm.markInTransit()
                    Status.DELIVERED -> vm.markDelivered()
                }
            },
            onDismiss = { confirmTarget = null },
        )
    }
    if (leftBehindOpen) {
        LeftBehindModal(d.orders.filter { it.reference !in state.checked }, onDismiss = { leftBehindOpen = false }) { actions, note ->
            leftBehindOpen = false
            vm.markPickedUp(actions, note)
        }
    }
    if (ordersOpen) {
        FvModal({ ordersOpen = false }, if (isPickup) "Orders in this pickup" else "Orders in this drop-off") {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                d.orders.forEachIndexed { i, o ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                        Row {
                            T(o.name, 13.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            T(itemsLabel(o.packageCount), 13.sp, Fv.TextFaint)
                        }
                        T("Sender: ${o.sender ?: "—"} · Recipient: ${o.recipient ?: "—"}", 13.sp, Fv.TextFaint, modifier = Modifier.padding(top = 2.dp))
                    }
                    if (i < d.orders.lastIndex) Rule()
                }
            }
            FvButton("Close", { ordersOpen = false }, Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
    personSheet?.let { c ->
        FvModal({ personSheet = null }, c.name) {
            ModalActions {
                FvButton("Call", { context.dial(c.phone) }, Modifier.weight(1f), icon = R.drawable.ic_phone, enabled = c.phone != null)
                FvButton("Text", { context.text(c.phone) }, Modifier.weight(1f), icon = R.drawable.ic_chat, enabled = c.phone != null)
            }
        }
    }
    contactPicker?.let { mode ->
        ContactPicker(mode, d) { contactPicker = null }
    }
}

/** Address heading with the PICK / DROP tag (and ⚑ for urgent drop-offs). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderTitle(d: StopDetail) {
    val isPickup = d.leg == Leg.PICKUP
    FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (d.urgent) Box(Modifier.align(Alignment.CenterVertically)) { UrgencyFlag() }
        LegTag(d.leg, if (isPickup) "PICK" else "DROP", Modifier.align(Alignment.CenterVertically))
        H1(d.address + if (!isPickup && d.buzzer != null) ", Buzzer: ${d.buzzer}" else "")
    }
}

private fun itemsLabel(n: Int) = "$n item${if (n == 1) "" else "s"}"

/** .pickup-checklist: round checkboxes, name, "N items", ⓘ. Ticked rows dim to 55 %. */
@Composable
private fun Checklist(orders: List<StopOrder>, checked: Set<String>, onToggle: (String) -> Unit, onInfo: (String) -> Unit) {
    Column(Modifier.padding(top = 6.dp)) {
        orders.forEach { o ->
            val on = o.reference in checked
            Row(
                Modifier.fillMaxWidth().heightIn(min = 32.dp).alpha(if (on) 0.55f else 1f).clickable { onToggle(o.reference) }.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.size(18.dp).clip(CircleShape).background(if (on) Fv.Accent else Fv.SurfaceAlt).border(2.dp, if (on) Fv.Accent else Fv.BorderStrong, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (on) FvIcon(R.drawable.ic_check, 10.dp, Color.White) }
                T(o.name, 13.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                T(itemsLabel(o.packageCount), 11.5.sp, Fv.TextFaint, modifier = Modifier.clickable { onInfo(o.reference) }.padding(horizontal = 6.dp))
                T("ⓘ", 15.sp, Fv.TextFaint, modifier = Modifier.clickable { onInfo(o.reference) }.padding(horizontal = 4.dp))
            }
        }
    }
}

/** "Are you sure? N orders not picked up." — reason + Restart / Fully Fail per order. */
@Composable
private fun LeftBehindModal(orders: List<StopOrder>, onDismiss: () -> Unit, onConfirm: (Map<String, String>, String) -> Unit) {
    val actions = remember { mutableStateMapOf<String, String>().apply { orders.forEach { put(it.reference, "restart") } } }
    var note by remember { mutableStateOf("") }
    FvModal(onDismiss) {
        Text(
            buildAnnotatedString {
                append("Are you sure? ")
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append("${orders.size}") }
                append(" orders not picked up.")
            },
            Modifier.padding(end = 24.dp, bottom = 8.dp), color = Fv.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        )
        FormGroup(null) {
            FormLabel("Reason Required:", color = Fv.Text, size = 13.sp)
            FvInput(note, { note = it }, "What's going on with them?", minLines = 2)
        }
        Text(
            buildAnnotatedString {
                append("Tap to either ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Fv.Text)) { append("Restart") }
                append(" later today, or ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Fv.Text)) { append("Fully Fail") }
                append(" it for Dispatch to manage.")
            },
            Modifier.padding(bottom = 8.dp), color = Fv.TextMuted, fontSize = 12.5.sp,
        )
        Column(Modifier.heightIn(min = 80.dp, max = 280.dp).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            orders.forEachIndexed { i, o ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("${o.name},") }
                            withStyle(SpanStyle(color = Fv.TextFaint)) { append(" ${itemsLabel(o.packageCount)}") }
                        },
                        Modifier.weight(1f), color = Fv.Text, fontSize = 13.sp,
                    )
                    val fail = actions[o.reference] == "fail"
                    val shape = RoundedCornerShape(Fv.RadiusMd)
                    Box(
                        Modifier.clip(shape).background(if (fail) Fv.DangerSoft else Fv.AccentSoft).border(1.dp, if (fail) Fv.Danger else Fv.Accent, shape)
                            .clickable { actions[o.reference] = if (fail) "restart" else "fail" }.padding(horizontal = 12.dp, vertical = 6.dp),
                    ) { T(if (fail) "Fully Fail" else "Restart", 12.sp, if (fail) Fv.Danger else Fv.AccentHover, FontWeight.Bold) }
                }
                if (i < orders.lastIndex) Rule()
            }
        }
        ModalActions {
            FvButton("Cancel", onDismiss, Modifier.weight(1f))
            FvButton("Mark Picked Up", { onConfirm(actions.toMap(), note) }, Modifier.weight(1f), BtnKind.Primary, enabled = note.isNotBlank())
        }
    }
}

/** Pickup Call / Text: the pickup contact, then sender + recipient of every order. */
@Composable
private fun ContactPicker(mode: String, d: StopDetail, onDismiss: () -> Unit) {
    val context = LocalContext.current
    fun act(phone: String?) { if (mode == "call") context.dial(phone) else context.text(phone) }
    val icon = if (mode == "call") R.drawable.ic_phone else R.drawable.ic_chat
    FvModal(onDismiss, if (mode == "call") "Call" else "Text") {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
            d.contacts.firstOrNull()?.let { c ->
                ContactRow("${d.business ?: ""}${if (d.business != null) " · " else ""}${c.name}", null, icon) { act(c.phone) }
            }
            d.orders.forEach { o ->
                Rule(Modifier.padding(top = 6.dp))
                T(o.name.uppercase(), 11.sp, Fv.TextFaint, FontWeight.Bold, Modifier.padding(top = 10.dp, bottom = 2.dp))
                ContactRow("Sender", o.sender ?: "—", icon) { act(o.senderPhone) }
                ContactRow("Recipient", o.recipient ?: "—", icon) { act(o.recipientPhone) }
            }
        }
    }
}

@Composable
private fun ContactRow(role: String, name: String?, icon: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        T(if (name == null) role else "$role · $name", 12.5.sp, modifier = Modifier.weight(1f))
        RoundIconButton(icon, onClick = onClick)
    }
}

/** Proof of delivery form (shown once In Transit). */
@Composable
private fun ProofOfDeliveryForm(state: StopState, vm: StopViewModel, nav: NavController, d: StopDetail) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            H3("Proof of delivery")
            FvBadge("Required", Tone.Danger)
        }
        OfflineNote("No signal? Photos still save — they'll auto-upload once you're back online.")
        FvButton(
            if (state.photos.isEmpty()) "Take Photo (Minimum 1)" else "Take Photo",
            { nav.navigate(Routes.camera(d.address)) },
            Modifier.fillMaxWidth().padding(bottom = 10.dp), leadingText = "📷", enabled = state.photos.size < 3,
        )
        if (state.photos.isNotEmpty()) PhotoGrid(state.photos, onRemove = vm::removePhoto)
        FormGroup("Where was it left?") { FvInput(state.leftAt, vm::setLeftAt, "e.g. Front desk, concierge, porch") }
        FormGroup("Customer contacted?") {
            ToggleGroup(listOf("not_needed" to "Not needed", "called" to "Called", "texted" to "Texted"), state.customerContact, vm::setContact)
        }
        FormGroup("Recipient name (optional)") { FvInput(state.recipientName, vm::setRecipient, "Who received it, if worth noting") }
        Rule()
    }
}

/** .photo-grid: 3 columns of 4:3 thumbs with a red ✕. */
@Composable
private fun PhotoGrid(photos: List<LocalPhoto>, onRemove: ((LocalPhoto) -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until 3) {
            val p = photos.getOrNull(i)
            Box(Modifier.weight(1f)) { if (p != null) PhotoThumb(p, onRemove) }
        }
    }
}

@Composable
private fun PhotoThumb(photo: LocalPhoto, onRemove: ((LocalPhoto) -> Unit)?) {
    val bitmap by produceState<Bitmap?>(null, photo.file) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(photo.file.absolutePath, BitmapFactory.Options().apply { inSampleSize = 8 }) }
    }
    val shape = RoundedCornerShape(Fv.RadiusSm)
    Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(shape).background(Fv.NeutralSoft).border(1.dp, Fv.BorderStrong, shape), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: FvIcon(R.drawable.ic_image, 30.dp, Fv.TextFaint)
        if (photo.uploading) androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), color = Fv.Accent, strokeWidth = 2.dp)
        if (onRemove != null) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(2.dp).size(18.dp).clip(CircleShape).background(Fv.Danger).clickable { onRemove(photo) },
                contentAlignment = Alignment.Center,
            ) { T("✕", 10.sp, Color.White) }
        }
    }
}

// ---------------------------------------------------------------------------
// Finished stop (task-detail-*-done.html)
// ---------------------------------------------------------------------------

@Composable
private fun Done(d: StopDetail, state: StopState, nav: NavController, modifier: Modifier) {
    val isPickup = d.leg == Leg.PICKUP
    Column(modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                H1(d.address)
                T(d.orders.map { it.name }.let { if (it.size <= 2) it.joinToString(" & ") else it.take(2).joinToString(" & ") + ", +${it.size - 2} orders" }, 13.sp)
            }
            Spacer(Modifier.width(10.dp))
            FvBadge(if (d.status == Status.FAILED) "Failed" else "Done", if (d.status == Status.FAILED) Tone.Danger else Tone.Success, Modifier.padding(top = 4.dp))
        }
        Section {
            H3(if (isPickup) "Pickup details" else "Delivery details", Modifier.padding(bottom = 8.dp))
            if (isPickup) {
                d.business?.let { InfoRow("Business", it) }
            } else {
                d.contacts.firstOrNull { it.role == "recipient" }?.let { InfoRow("Recipient", it.name) }
                d.contacts.firstOrNull { it.role == "sender" }?.let { InfoRow("Sender", it.name) }
            }
            val completed = d.proof?.completedAt ?: d.timeline.lastOrNull()?.at
            InfoRow(if (d.status == Status.FAILED) "Failed" else "Completed", timeLabel(completed))
        }
        Section {
            val steps = if (isPickup) listOf(Status.ASSIGNED, Status.PICKING_UP, Status.PICKED_UP)
            else listOf(Status.PICKED_UP, Status.IN_TRANSIT, if (d.status == Status.FAILED) Status.FAILED else Status.DELIVERED)
            val times = d.timeline.associate { it.status to timeLabel(it.at) }
            StatusStepper(steps.map { Step(Status.label(it), times[it]) }, doneCount = steps.size, currentIndex = null, timeBelow = true)
        }
        if (!isPickup && d.proof != null) {
            val p = d.proof
            Section {
                H3(if (p.outcome == "failed") "Failure report" else "Proof of delivery", Modifier.padding(bottom = 8.dp))
                if (p.photoCount > 0) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in 0 until 3) {
                            Box(Modifier.weight(1f)) {
                                if (i < p.photoCount) {
                                    val shape = RoundedCornerShape(Fv.RadiusSm)
                                    Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(shape).background(Fv.NeutralSoft).border(1.dp, Fv.BorderStrong, shape), contentAlignment = Alignment.Center) {
                                        FvIcon(R.drawable.ic_image, 40.dp, Fv.TextFaint)
                                    }
                                }
                            }
                        }
                    }
                }
                p.leftAt?.let { LockedField("Where was it left?", it) }
                p.customerContact?.let { LockedField("Customer contacted?", mapOf("not_needed" to "Not needed", "called" to "Called", "texted" to "Texted")[it] ?: it, bold = true) }
                p.recipientName?.let { LockedField("Recipient name (optional)", it) }
                p.failureReason?.let { LockedField("Reason", it.replace('_', ' ').replaceFirstChar(Char::uppercase)) }
                p.note?.let { LockedField("Note", it) }
            }
        }
        if (isPickup && state.pickupResult != null) PickupResult(state, d, nav)
        Spacer(Modifier.height(10.dp))
        Text(
            buildAnnotatedString {
                append(
                    when {
                        !isPickup && d.status == Status.FAILED -> "Reported to Dispatch — see other stops on the "
                        !isPickup -> "Delivered — see other stops on the "
                        d.orders.size == 2 -> "Both orders are now In Transit for delivery — see their delivery stops on the "
                        d.orders.size > 2 -> "All orders are now In Transit for delivery — see their delivery stops on the "
                        else -> "The order is now In Transit for delivery — see its delivery stop on the "
                    },
                )
                withStyle(SpanStyle(color = Fv.Accent)) { append("task list") }
                append(".")
            },
            Modifier.clickable { nav.navigate(Routes.TASKS) { popUpTo(Routes.TASKS) { inclusive = true } } },
            color = Fv.TextMuted, fontSize = 14.5.sp, lineHeight = 20.sp,
        )
        Spacer(Modifier.height(20.dp))
    }
}

/** .pickup-summary: Picked up (n) with go-to-delivery buttons, Restarted (n), Fully failed (n). */
@Composable
private fun PickupResult(state: StopState, d: StopDetail, nav: NavController) {
    val r = state.pickupResult ?: return
    val name = d.orders.associate { it.reference to it.name }
    Section {
        H3("Pickup Result", Modifier.padding(bottom = 8.dp))
        if (r.pickedUp.isNotEmpty()) {
            T("PICKED UP (${r.pickedUp.size})", 11.sp, Fv.Success, FontWeight.Bold, Modifier.padding(bottom = 4.dp))
            r.pickedUp.forEach { p ->
                Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    T(name[p.reference] ?: p.reference, 13.sp)
                    p.dropoffStopKey?.let { key -> RoundIconButton(R.drawable.ic_arrow_right, 40.dp, 16.dp) { nav.navigate(Routes.stop(key)) } }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        if (r.restarted.isNotEmpty()) {
            T("RESTARTED (${r.restarted.size})", 11.sp, Fv.Accent, FontWeight.Bold, Modifier.padding(bottom = 4.dp))
            r.restarted.forEach { T(name[it] ?: it, 13.sp, modifier = Modifier.padding(vertical = 3.dp)) }
            Spacer(Modifier.height(12.dp))
        }
        if (r.failed.isNotEmpty()) {
            T("FULLY FAILED (${r.failed.size})", 11.sp, Fv.Danger, FontWeight.Bold, Modifier.padding(bottom = 4.dp))
            r.failed.forEach { T(name[it] ?: it, 13.sp, modifier = Modifier.padding(vertical = 3.dp)) }
        }
    }
}

/** .info-card: 10 dp vertical padding with a bottom rule. */
@Composable
private fun Section(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), content = content)
    Rule()
}

/** Locked proof-of-delivery field: label + plain value. */
@Composable
private fun LockedField(label: String, value: String, bold: Boolean = false) {
    Column(Modifier.padding(bottom = 12.dp)) {
        FormLabel(label)
        T(value, if (bold) 12.sp else 13.sp, weight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun OrderInfoRow(label: String, value: String) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            T(label, 13.sp, Fv.TextFaint)
            Spacer(Modifier.weight(1f))
            T(value, 13.sp, weight = FontWeight.SemiBold)
        }
        Rule()
    }
}
