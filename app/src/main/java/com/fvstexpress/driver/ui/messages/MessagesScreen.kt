package com.fvstexpress.driver.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.fvstexpress.driver.AppContainer
import com.fvstexpress.driver.R
import com.fvstexpress.driver.data.model.Leg
import com.fvstexpress.driver.data.model.MessageDto
import com.fvstexpress.driver.data.repo.ApiResult
import com.fvstexpress.driver.ui.components.AppShell
import com.fvstexpress.driver.ui.components.BottomTab
import com.fvstexpress.driver.ui.components.EmptyState
import com.fvstexpress.driver.ui.components.ErrorState
import com.fvstexpress.driver.ui.components.FvIcon
import com.fvstexpress.driver.ui.components.Loading
import com.fvstexpress.driver.ui.components.Rule
import com.fvstexpress.driver.ui.components.T
import com.fvstexpress.driver.ui.components.TopBar
import com.fvstexpress.driver.ui.components.TopIcon
import com.fvstexpress.driver.ui.components.TopTitle
import com.fvstexpress.driver.ui.nav.Routes
import com.fvstexpress.driver.ui.theme.Fv
import com.fvstexpress.driver.ui.util.timeLabel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime

/** The inbox overlay: unread rows tinted + bold, tap to expand (marks read), ☆ to star. */
@Composable
fun MessagesScreen(nav: NavController, container: AppContainer) {
    val repo = container.repository
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<MessageDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<Int?>(null) }
    var starredOnly by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        when (val r = repo.messages()) {
            is ApiResult.Ok -> messages = r.data.messages
            is ApiResult.Err -> error = r.message
            ApiResult.Queued -> Unit
        }
    }

    fun update(id: Int, change: (MessageDto) -> MessageDto) { messages = messages?.map { if (it.id == id) change(it) else it } }

    AppShell(
        nav = nav, container = container, tab = BottomTab.NONE, showBottomNav = false,
        topBar = {
            TopBar(
                left = { TopIcon(R.drawable.ic_arrow_left, "Back") { nav.popBackStack() } },
                center = { TopTitle("Messages") },
                right = {
                    TopIcon(if (starredOnly) R.drawable.ic_star_filled else R.drawable.ic_star, "Show starred only", if (starredOnly) Fv.Warning else Fv.Text) {
                        starredOnly = !starredOnly
                    }
                },
            )
        },
    ) { modifier ->
        val list = messages
        when {
            list == null && error != null -> Box(modifier) { ErrorState(error!!) }
            list == null -> Loading(modifier)
            else -> {
                val shown = if (starredOnly) list.filter { it.starred } else list
                LazyColumn(modifier) {
                    if (shown.isEmpty()) item { EmptyState(if (starredOnly) "No starred messages." else "No messages.") }
                    items(shown, key = { it.id }) { m ->
                        val open = expanded == m.id
                        Column(Modifier.fillMaxWidth().background(if (m.read) Color.Transparent else Fv.AccentSoft)) {
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    expanded = if (open) null else m.id
                                    if (!m.read) { update(m.id) { it.copy(read = true) }; scope.launch { repo.markRead(m.id) } }
                                }.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    T(m.orderName ?: "Dispatch", 13.sp, weight = if (m.read) FontWeight.Normal else FontWeight.Bold)
                                    T(m.subject, 12.5.sp, Fv.TextMuted, if (m.read) FontWeight.Normal else FontWeight.Bold, Modifier.padding(top = 1.dp))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    T(sentLabel(m.sentAt), 11.sp, Fv.TextFaint)
                                    Box(Modifier.size(21.dp).clickable {
                                        val star = !m.starred
                                        update(m.id) { it.copy(starred = star) }
                                        scope.launch { repo.star(m.id, star) }
                                    }, contentAlignment = Alignment.Center) {
                                        FvIcon(if (m.starred) R.drawable.ic_star_filled else R.drawable.ic_star, 17.dp, if (m.starred) Fv.Warning else Fv.TextFaint, if (m.starred) "Unstar" else "Star")
                                    }
                                }
                            }
                            if (open) {
                                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                                    T(m.body, 13.sp, Fv.TextMuted, lineHeight = 19.5.sp)
                                    m.stopKey?.let { key ->
                                        Row(
                                            Modifier.padding(top = 8.dp).clickable { nav.navigate(Routes.stop(key)) },
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            FvIcon(R.drawable.ic_arrow_right, 14.dp, Fv.AccentHover)
                                            T(if (m.leg == Leg.PICKUP) "Go to Pickup page" else "Go to Dropoff page", 12.5.sp, Fv.AccentHover, FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                            Rule()
                        }
                    }
                }
            }
        }
    }
}

/** "10:42 AM" today, "Yesterday", else the date. */
private fun sentLabel(iso: String): String {
    val t = runCatching { OffsetDateTime.parse(iso) }.getOrNull() ?: return timeLabel(iso)
    val day = t.toLocalDate()
    val today = LocalDate.now()
    return when (day) {
        today -> timeLabel(iso)
        today.minusDays(1) -> "Yesterday"
        else -> "${day.month.name.lowercase().replaceFirstChar(Char::uppercase).take(3)} ${day.dayOfMonth}"
    }
}
