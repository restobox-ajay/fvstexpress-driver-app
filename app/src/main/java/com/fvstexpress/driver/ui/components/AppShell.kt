package com.fvstexpress.driver.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.fvstexpress.driver.AppContainer
import com.fvstexpress.driver.BuildConfig
import com.fvstexpress.driver.R
import com.fvstexpress.driver.data.location.LocationService
import com.fvstexpress.driver.data.repo.ApiResult
import com.fvstexpress.driver.ui.connectivity.ConnectivityModal
import com.fvstexpress.driver.ui.nav.Routes
import com.fvstexpress.driver.ui.theme.Fv
import com.fvstexpress.driver.ui.util.dial
import kotlinx.coroutines.launch

enum class BottomTab { TASKS, ANALYTICS, NONE }

/** The online toggle in every top bar: asks for location once, starts / stops GPS sharing. */
@Composable
fun rememberOnlineToggle(container: AppContainer): Pair<Boolean, (Boolean) -> Unit> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val online by container.online.collectAsState()

    fun apply(on: Boolean) {
        container.online.value = on
        scope.launch {
            when (val r = container.repository.setOnline(on)) {
                is ApiResult.Ok -> {
                    container.online.value = r.data
                    if (r.data) LocationService.start(context) else LocationService.stop(context)
                }
                is ApiResult.Err -> container.online.value = !on
                ApiResult.Queued -> Unit
            }
        }
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { apply(true) }
    return online to { on ->
        if (on && !LocationService.hasLocationPermission(context)) {
            val asks = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) asks += Manifest.permission.POST_NOTIFICATIONS
            permissions.launch(asks.toTypedArray())
        } else {
            apply(on)
        }
    }
}

/** .topbar-icon-btn: 19 dp icon in a 31 dp tap target. */
@Composable
fun TopIcon(@DrawableRes icon: Int, description: String, tint: Color = Fv.Text, badge: Int = 0, onClick: () -> Unit) {
    Box(Modifier.size(31.dp).clip(RoundedCornerShape(Fv.RadiusSm)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        FvIcon(icon, 19.dp, tint, description)
        if (badge > 0) {
            // .messages-badge: 15 dp red pill, top-right
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 2.dp).heightIn(min = 15.dp).widthIn(min = 15.dp)
                    .clip(RoundedCornerShape(999.dp)).background(Fv.Danger).padding(horizontal = 3.dp),
                contentAlignment = Alignment.Center,
            ) { T("$badge", 10.sp, Color.White, FontWeight.ExtraBold) }
        }
    }
}

/** .driver-topbar: 52 dp, surface, 1px bottom border; left / centre / right with equal sides. */
@Composable
fun TopBar(
    left: @Composable RowScope.() -> Unit = {},
    right: @Composable RowScope.() -> Unit = {},
    center: @Composable () -> Unit = {},
) {
    Column(Modifier.background(Fv.Surface).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            Modifier.fillMaxWidth().height(Fv.TopBarHeight).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, content = left)
            center()
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically, content = right)
        }
        Rule()
    }
}

@Composable
fun TopTitle(text: String) = T(text, 16.3.sp, weight = FontWeight.Bold)

/** State shared by a screen's chrome (drawer + connectivity modal). */
class ShellState {
    var drawerOpen by mutableStateOf(false)
    var connectivityOpen by mutableStateOf(false)
}

/**
 * The phone frame of every logged-in page: top bar, scrollable content on --color-bg, and the
 * Tasks / Analytics / Call Dispatch bottom nav. The ☰ drawer and the Connectivity modal live here.
 */
@Composable
fun AppShell(
    nav: NavController,
    container: AppContainer,
    tab: BottomTab,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    shell: ShellState = remember { ShellState() },
    showBottomNav: Boolean = true,
    topBar: @Composable () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by container.session.session.collectAsState()
    val dispatchPhone = session?.driver?.dispatchPhone ?: BuildConfig.DISPATCH_PHONE

    Box(Modifier.fillMaxSize().background(Fv.Bg)) {
        Column(Modifier.fillMaxSize()) {
            topBar()
            Box(Modifier.weight(1f).fillMaxWidth().imePadding()) { content(Modifier.fillMaxSize()) }
            if (showBottomNav) {
                Column(Modifier.background(Fv.Surface).windowInsetsPadding(WindowInsets.navigationBars)) {
                    Rule()
                    Row(Modifier.fillMaxWidth().height(Fv.BottomNavHeight)) {
                        NavItem(R.drawable.ic_map_pin, "Tasks", tab == BottomTab.TASKS) {
                            if (tab != BottomTab.TASKS) nav.navigate(Routes.TASKS) { popUpTo(Routes.TASKS) { inclusive = true } }
                        }
                        NavItem(R.drawable.ic_bar_chart, "Analytics", tab == BottomTab.ANALYTICS) {
                            if (tab != BottomTab.ANALYTICS) nav.navigate(Routes.ANALYTICS) { popUpTo(Routes.TASKS) }
                        }
                        NavItem(R.drawable.ic_phone, "Call Dispatch", false) { context.dial(dispatchPhone) }
                    }
                }
            } else {
                Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 70.dp)) { data ->
            Snackbar(data, containerColor = Fv.SurfaceAlt, contentColor = Fv.Text, shape = RoundedCornerShape(Fv.RadiusMd))
        }

        Drawer(
            open = shell.drawerOpen,
            onClose = { shell.drawerOpen = false },
            onProfile = { shell.drawerOpen = false; nav.navigate(Routes.PROFILE) },
            onConnectivity = { shell.drawerOpen = false; shell.connectivityOpen = true },
            onLogout = { shell.drawerOpen = false; scope.launch { container.repository.logout() } },
        )
    }
    if (shell.connectivityOpen) ConnectivityModal(container) { shell.connectivityOpen = false }
}

@Composable
private fun RowScope.NavItem(@DrawableRes icon: Int, label: String, active: Boolean, onClick: () -> Unit) {
    val color = if (active) Fv.Accent else Fv.Text
    Column(
        Modifier.weight(1f).fillMaxHeight().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        FvIcon(icon, 19.dp, color)
        T(label, 10.5.sp, color, FontWeight.Bold)
    }
}

/** .sidebar-drawer: 78 % (max 300 dp) panel from the left over a 50 % black scrim. */
@Composable
private fun Drawer(open: Boolean, onClose: () -> Unit, onProfile: () -> Unit, onConnectivity: () -> Unit, onLogout: () -> Unit) {
    AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(remember { MutableInteractionSource() }, null, onClick = onClose))
    }
    AnimatedVisibility(open, enter = slideInHorizontally { -it }, exit = slideOutHorizontally { -it }) {
        BoxWithConstraints(Modifier.fillMaxHeight()) {
            Column(
                Modifier.fillMaxHeight().width(minOf(maxWidth * 0.78f, 300.dp)).background(Fv.Surface)
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                Row(Modifier.fillMaxWidth().height(Fv.TopBarHeight).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    T("Menu", 14.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TopIcon(R.drawable.ic_close, "Close menu", onClick = onClose)
                }
                Rule()
                Column(Modifier.padding(6.dp)) {
                    DrawerItem(R.drawable.ic_user, "Profile", Fv.Text, Fv.Text, onProfile)
                    DrawerItem(R.drawable.ic_wifi, "Connectivity", Fv.Success, Fv.Text, onConnectivity)
                    Box(Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) { Rule() }
                    DrawerItem(R.drawable.ic_logout, "Log Out", Fv.Text, Fv.Text, onLogout)
                }
            }
            Box(Modifier.align(Alignment.TopEnd).fillMaxHeight().width(1.dp).background(Fv.BorderStrong))
        }
    }
}

@Composable
private fun DrawerItem(@DrawableRes icon: Int, label: String, iconTint: Color, textColor: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Fv.RadiusSm)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FvIcon(icon, 19.dp, iconTint)
        T(label, 14.sp, textColor, FontWeight.SemiBold)
    }
}

