package com.fvstexpress.driver.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.material3.Text
import com.fvstexpress.driver.AppContainer
import com.fvstexpress.driver.R
import com.fvstexpress.driver.data.location.LocationService
import com.fvstexpress.driver.data.model.Leg
import com.fvstexpress.driver.data.model.StopSummary
import com.fvstexpress.driver.ui.components.AppShell
import com.fvstexpress.driver.ui.components.BottomTab
import com.fvstexpress.driver.ui.components.DayGraphBar
import com.fvstexpress.driver.ui.components.EmptyState
import com.fvstexpress.driver.ui.components.ErrorState
import com.fvstexpress.driver.ui.components.LegTag
import com.fvstexpress.driver.ui.components.Loading
import com.fvstexpress.driver.ui.components.OfflineNote
import com.fvstexpress.driver.ui.components.OnlineToggle
import com.fvstexpress.driver.ui.components.Rule
import com.fvstexpress.driver.ui.components.Segmented
import com.fvstexpress.driver.ui.components.ShellState
import com.fvstexpress.driver.ui.components.T
import com.fvstexpress.driver.ui.components.TopBar
import com.fvstexpress.driver.ui.components.TopIcon
import com.fvstexpress.driver.ui.components.UrgencyFlag
import com.fvstexpress.driver.ui.components.rememberOnlineToggle
import com.fvstexpress.driver.ui.nav.Routes
import com.fvstexpress.driver.ui.theme.Fv
import com.fvstexpress.driver.ui.util.timeLabel

/** tasks.html: Today / All / Completed task list, or the route map (pin icon toggles). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(nav: NavController, container: AppContainer) {
    val context = LocalContext.current
    val vm: TasksViewModel = viewModel { TasksViewModel(container.repository, container.online.value) }
    val state by vm.state.collectAsStateWithLifecycle()
    val pending by container.repository.pending.collectAsState()
    val (online, setOnline) = rememberOnlineToggle(container)
    val shell = remember { ShellState() }
    val snackbar = remember { SnackbarHostState() }

    // Keep GPS sharing running after an app restart if the driver was left online.
    LaunchedEffect(Unit) { if (online) LocationService.start(context) }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }

    AppShell(
        nav = nav, container = container, tab = BottomTab.TASKS, snackbar = snackbar, shell = shell,
        topBar = {
            TopBar(
                left = {
                    TopIcon(R.drawable.ic_menu, "Open menu") { shell.drawerOpen = true }
                    TopIcon(R.drawable.ic_message_square, "Messages", badge = state.unread) { nav.navigate(Routes.MESSAGES) }
                },
                center = { OnlineToggle(online, setOnline) },
                right = {
                    if (state.showMap) TopIcon(R.drawable.ic_list, "Show list", onClick = vm::toggleMap)
                    else TopIcon(R.drawable.ic_map_pin, "Show map", onClick = vm::toggleMap)
                },
            )
        },
    ) { modifier ->
        Column(modifier) {
            if (state.showMap) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                    RouteMapView(state.route, onStop = vm::openPin)
                    state.pinSheet?.let { d -> PinSheet(d, onClose = vm::closePin) { vm.closePin(); nav.navigate(Routes.stop(d.stopKey)) } }
                }
                return@Column
            }
            Segmented(TaskView.entries.map { it.label }, state.view.ordinal) { vm.setView(TaskView.entries[it]) }
            if (pending.isNotEmpty()) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp)) {
                    OfflineNote("${pending.size} update(s) saved on this phone — they'll send to Dispatch once you're back online.")
                }
            }
            when {
                state.loading -> Loading()
                state.error != null && state.days.isEmpty() -> ErrorState(state.error!!) { vm.load() }
                else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.load(pull = true) }, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        if (state.days.all { it.stops.isEmpty() }) {
                            item { EmptyState(if (state.view == TaskView.COMPLETED) "Nothing completed yet today." else "No tasks right now. Pull down to refresh.") }
                        }
                        val headers = state.view == TaskView.ALL
                        state.days.forEach { day ->
                            if (headers) item(key = "h-${day.date}") {
                                // .task-list-date-header
                                T(day.label.uppercase(), 11.sp, Fv.TextFaint, FontWeight.Bold, Modifier.padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 6.dp))
                            }
                            items(day.stops, key = { "${day.date}-${it.stopKey}" }) { stop ->
                                TaskCard(stop) { nav.navigate(Routes.stop(stop.stopKey)) }
                                Rule()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** .task-card: leg tag + order(s), business, bold address · contact, time + day graph, chevron. */
@Composable
fun TaskCard(stop: StopSummary, onClick: () -> Unit) {
    val title = stop.orders.take(2).joinToString(" & ") { it.name }
    val start = stop.window?.start
    val end = stop.window?.end
    val time = when {
        stop.completedAt != null -> "Completed ${timeLabel(stop.completedAt)}"
        stop.leg == Leg.PICKUP -> end?.let { "By ${timeLabel(it)}" } ?: timeLabel(start)
        else -> timeLabel(end ?: start)
    }
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (stop.urgent) UrgencyFlag()
            LegTag(stop.leg)
            Text(title, Modifier.weight(1f), color = Fv.Text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            T("›", 16.sp, Fv.TextFaint)
        }
        stop.business?.let { T(it, 12.sp, Fv.TextMuted, modifier = Modifier.padding(top = 1.dp)) }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Fv.Text)) { append(stop.address) }
                stop.contact?.let { withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = Fv.TextMuted)) { append(" · $it") } }
            },
            Modifier.padding(top = 2.dp),
        )
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            T(time, 11.5.sp, Fv.Text)
            Spacer(Modifier.width(8.dp))
            DayGraphBar(start, end, Modifier.weight(1f))
        }
    }
}


/** .map-pin-sheet: bottom sheet with leg tag, order, address, multi-order table, "View details". */
@Composable
private fun PinSheet(d: com.fvstexpress.driver.data.model.StopDetail, onClose: () -> Unit, onDetails: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
            .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        Column(
            Modifier.fillMaxWidth().clip(shape).background(Fv.Surface)
                .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) {}
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LegTag(d.leg, if (d.leg == Leg.PICKUP) "PICK" else "DROP")
                T(d.orders.take(2).joinToString(" & ") { it.name }, 14.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TopIcon(R.drawable.ic_close, "Close", onClick = onClose)
            }
            T(d.address, 13.sp, Fv.TextMuted, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
            if (d.orders.size > 1) {
                val tshape = androidx.compose.foundation.shape.RoundedCornerShape(Fv.RadiusSm)
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(tshape)
                        .border(1.dp, Fv.Border, tshape).heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                ) {
                    Row(Modifier.fillMaxWidth().background(Fv.SurfaceAlt).padding(horizontal = 8.dp, vertical = 6.dp)) {
                        T("Order Name", 12.sp, Fv.TextFaint, FontWeight.Bold, Modifier.weight(1.2f))
                        T("Recipient", 12.sp, Fv.TextFaint, FontWeight.Bold, Modifier.weight(1f))
                        T("Items", 12.sp, Fv.TextFaint, FontWeight.Bold, Modifier.weight(0.5f))
                    }
                    Rule()
                    d.orders.forEach { o ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                            T(o.name, 12.sp, modifier = Modifier.weight(1.2f))
                            T(o.recipient ?: "—", 12.sp, modifier = Modifier.weight(1f))
                            T("${o.packageCount}", 12.sp, modifier = Modifier.weight(0.5f))
                        }
                        Rule()
                    }
                }
            }
            com.fvstexpress.driver.ui.components.FvButton("View details", onDetails, Modifier.fillMaxWidth(), com.fvstexpress.driver.ui.components.BtnKind.Primary)
        }
    }
}
