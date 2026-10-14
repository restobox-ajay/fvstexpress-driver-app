package com.fvstexpress.driver.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.fvstexpress.driver.AppContainer
import com.fvstexpress.driver.R
import com.fvstexpress.driver.data.model.WorkToday
import com.fvstexpress.driver.data.repo.ApiResult
import com.fvstexpress.driver.ui.components.AppShell
import com.fvstexpress.driver.ui.components.BottomTab
import com.fvstexpress.driver.ui.components.BtnKind
import com.fvstexpress.driver.ui.components.FormHelp
import com.fvstexpress.driver.ui.components.FvButton
import com.fvstexpress.driver.ui.components.H2
import com.fvstexpress.driver.ui.components.OnlineToggle
import com.fvstexpress.driver.ui.components.Rule
import com.fvstexpress.driver.ui.components.StatBox
import com.fvstexpress.driver.ui.components.T
import com.fvstexpress.driver.ui.components.TopBar
import com.fvstexpress.driver.ui.components.TopIcon
import com.fvstexpress.driver.ui.components.TopTitle
import com.fvstexpress.driver.ui.components.rememberOnlineToggle
import com.fvstexpress.driver.ui.theme.Fv
import com.fvstexpress.driver.ui.util.minutesLabel
import com.fvstexpress.driver.ui.util.timeLabel
import kotlinx.coroutines.launch

/** profile.html: avatar, online toggle, today's working hours, breaks, log out. */
@Composable
fun ProfileScreen(nav: NavController, container: AppContainer) {
    val repo = container.repository
    val session by container.session.session.collectAsState()
    val (online, setOnline) = rememberOnlineToggle(container)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var work by remember { mutableStateOf<WorkToday?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun reload() { (repo.workToday() as? ApiResult.Ok)?.let { work = it.data } }
    LaunchedEffect(Unit) { repo.me(); reload() }

    fun breakTap() {
        val onBreak = work?.onBreak == true
        busy = true
        scope.launch {
            when (val r = repo.work(if (onBreak) "break_end" else "break_start")) {
                is ApiResult.Err -> snackbar.showSnackbar(r.message)
                ApiResult.Queued -> snackbar.showSnackbar("Saved on this phone — it'll send when you're back online.")
                is ApiResult.Ok -> Unit
            }
            reload()
            busy = false
        }
    }

    AppShell(
        nav = nav, container = container, tab = BottomTab.NONE, snackbar = snackbar,
        topBar = {
            TopBar(
                left = { TopIcon(R.drawable.ic_arrow_left, "Back") { nav.popBackStack() } },
                center = { TopTitle("Profile") },
            )
        },
    ) { modifier ->
        val driver = session?.driver
        Column(modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
            // Avatar + name
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val initials = driver?.name?.split(" ")?.mapNotNull { it.firstOrNull()?.uppercaseChar() }?.take(2)?.joinToString("") ?: ""
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Fv.Accent), contentAlignment = Alignment.Center) {
                    T(initials, 15.sp, Color.White, FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(10.dp))
                H2(driver?.name ?: "")
                T(listOfNotNull(driver?.vehicleType, driver?.employeeCardNumber).joinToString(" · "), 14.5.sp, Fv.TextMuted)
            }
            Rule()
            Row(Modifier.padding(vertical = 14.dp)) { OnlineToggle(online, setOnline) }

            H2("Today's working hours")
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox(minutesLabel(work?.workedMinutes ?: 0), "Worked so far", Modifier.weight(1f))
                StatBox(work?.startedAt?.let { timeLabel(it) } ?: "—", "Started at", Modifier.weight(1f))
            }
            FormHelp("Calculated from your first Picked Up task to your final Delivered status, minus any break time.", Modifier.padding(top = 6.dp))
            FvButton(
                if (work?.onBreak == true) "End break" else "Start break", { breakTap() },
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp), leadingText = "☕", enabled = work?.startedAt != null, busy = busy,
            )
            Rule()
            Column(Modifier.padding(vertical = 10.dp)) {
                H2("Breaks today", Modifier.padding(bottom = 4.dp))
                val breaks = work?.breaks.orEmpty()
                if (breaks.isEmpty()) T("No break taken yet today.", 14.5.sp, Fv.TextMuted)
                breaks.forEach { b -> T("${timeLabel(b.start)} – ${b.end?.let { timeLabel(it) } ?: "now"}", 14.5.sp, Fv.TextMuted) }
            }
            Rule()
            FvButton("Log out", { scope.launch { repo.logout() } }, Modifier.fillMaxWidth().padding(top = 6.dp), BtnKind.Danger)
        }
    }
}
