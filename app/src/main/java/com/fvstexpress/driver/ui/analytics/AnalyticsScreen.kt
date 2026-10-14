package com.fvstexpress.driver.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.fvstexpress.driver.AppContainer
import com.fvstexpress.driver.R
import com.fvstexpress.driver.data.model.AnalyticsSummary
import com.fvstexpress.driver.data.repo.ApiResult
import com.fvstexpress.driver.ui.components.AppShell
import com.fvstexpress.driver.ui.components.BottomTab
import com.fvstexpress.driver.ui.components.ErrorState
import com.fvstexpress.driver.ui.components.H2
import com.fvstexpress.driver.ui.components.Loading
import com.fvstexpress.driver.ui.components.Rule
import com.fvstexpress.driver.ui.components.ShellState
import com.fvstexpress.driver.ui.components.StatBox
import com.fvstexpress.driver.ui.components.T
import com.fvstexpress.driver.ui.components.TopBar
import com.fvstexpress.driver.ui.components.TopIcon
import com.fvstexpress.driver.ui.components.TopTitle
import com.fvstexpress.driver.ui.nav.Routes
import com.fvstexpress.driver.ui.theme.Fv

/** analytics.html: Today stat boxes + Daily Summary rows. */
@Composable
fun AnalyticsScreen(nav: NavController, container: AppContainer) {
    var data by remember { mutableStateOf<AnalyticsSummary?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var unread by remember { mutableIntStateOf(0) }
    var attempt by remember { mutableIntStateOf(0) }
    val shell = remember { ShellState() }

    LaunchedEffect(attempt) {
        error = null
        when (val r = container.repository.analytics()) {
            is ApiResult.Ok -> data = r.data
            is ApiResult.Err -> error = r.message
            ApiResult.Queued -> Unit
        }
        (container.repository.messages() as? ApiResult.Ok)?.let { unread = it.data.unread }
    }

    AppShell(
        nav = nav, container = container, tab = BottomTab.ANALYTICS, shell = shell,
        topBar = {
            TopBar(
                left = {
                    TopIcon(R.drawable.ic_menu, "Open menu") { shell.drawerOpen = true }
                    TopIcon(R.drawable.ic_message_square, "Messages", badge = unread) { nav.navigate(Routes.MESSAGES) }
                },
                center = { TopTitle("Analytics") },
                right = { TopIcon(R.drawable.ic_user, "Profile") { nav.navigate(Routes.PROFILE) } },
            )
        },
    ) { modifier ->
        val d = data
        when {
            d == null && error != null -> Box(modifier) { ErrorState(error!!) { attempt++ } }
            d == null -> Loading(modifier)
            else -> Column(modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
                H2("Today")
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBox("${d.today.deliveries}", "Deliveries today", Modifier.weight(1f))
                    StatBox("%.1fh".format(d.today.hours), "Hours today", Modifier.weight(1f))
                }
                Column(Modifier.padding(top = 20.dp, bottom = 10.dp)) {
                    H2("Daily Summary", Modifier.padding(bottom = 8.dp))
                    d.daily.forEach { day ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            T(if (day.today) "${day.label} (Today)" else day.label, 12.5.sp, Fv.TextFaint)
                            Spacer(Modifier.weight(1f))
                            T("${day.stops} stops · ${day.onTimePct}% on-time · ${"%.1f".format(day.hours)}h", 12.5.sp)
                        }
                    }
                }
                Rule()
            }
        }
    }
}
