package com.fvstexpress.driver.ui.connectivity

import android.content.Context
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fvstexpress.driver.AppContainer
import com.fvstexpress.driver.BuildConfig
import com.fvstexpress.driver.data.location.LocationHolder
import com.fvstexpress.driver.data.repo.ApiResult
import com.fvstexpress.driver.ui.components.BtnKind
import com.fvstexpress.driver.ui.components.FormGroup
import com.fvstexpress.driver.ui.components.FvButton
import com.fvstexpress.driver.ui.components.FvInput
import com.fvstexpress.driver.ui.components.FvModal
import com.fvstexpress.driver.ui.components.H3
import com.fvstexpress.driver.ui.components.Rule
import com.fvstexpress.driver.ui.components.T
import com.fvstexpress.driver.ui.theme.Fv
import kotlinx.coroutines.launch

private fun hasInternet(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private fun gpsEnabled(context: Context): Boolean {
    val lm = context.getSystemService(LocationManager::class.java) ?: return false
    return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

/** The ☰ › Connectivity popup: Internet / Dispatch Server / GPS status, then "Report Issue". */
@Composable
fun ConnectivityModal(container: AppContainer, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repo = container.repository
    val scope = rememberCoroutineScope()
    val session by container.session.session.collectAsState()
    val lastFix by LocationHolder.last.collectAsState()
    val pending by repo.pending.collectAsState()
    var serverOk by remember { mutableStateOf<Boolean?>(null) }
    var lastConnected by remember { mutableStateOf<Long?>(null) }
    val internet = remember { hasInternet(context) }
    val gps = remember { gpsEnabled(context) }
    var type by remember { mutableStateOf("connectivity") }
    var typeMenu by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf<String?>(null) }
    val types = listOf("connectivity" to "Connectivity", "app_function" to "App Function", "camera" to "Camera / Photo", "other" to "Other")

    LaunchedEffect(Unit) {
        val ok = repo.ping() is ApiResult.Ok
        serverOk = ok
        if (ok) lastConnected = System.currentTimeMillis()
    }

    FvModal(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            val d = session?.driver
            T("User ID: ${d?.employeeCardNumber ?: "—"} (${d?.name ?: ""})", 12.sp, Fv.TextFaint, modifier = Modifier.padding(end = 24.dp, bottom = 8.dp))
            H3("Connectivity", Modifier.padding(bottom = 4.dp))
            StatusRow("Internet", internet, null)
            StatusRow(
                "Dispatch Server", serverOk,
                lastConnected?.let { "Last connected ${(System.currentTimeMillis() - it) / 1000}s ago" } ?: if (serverOk == false) "Can't reach ${BuildConfig.API_BASE_URL}" else "Checking…",
            )
            StatusRow("GPS", gps, lastFix?.let { "Last ping ${(System.currentTimeMillis() - it.time) / 1000}s ago" } ?: "No GPS fix yet")
            T(
                if (pending.isEmpty()) "All photos and route progress are saved, and pushed to Dispatch server when connectivity resumes."
                else "${pending.size} update(s) waiting to send: " + pending.joinToString(", ") { it.label },
                12.sp, Fv.TextMuted, modifier = Modifier.padding(top = 8.dp),
            )

            Column(Modifier.padding(top = 14.dp)) {
                Rule()
                H3("Report Issue", Modifier.padding(top = 14.dp, bottom = 8.dp))
                FormGroup("Type") {
                    Box {
                        val shape = RoundedCornerShape(Fv.RadiusSm)
                        Row(
                            Modifier.fillMaxWidth().clip(shape).background(Fv.SurfaceAlt).border(1.dp, Fv.BorderStrong, shape)
                                .clickable { typeMenu = true }.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            T(types.first { it.first == type }.second, 13.sp, modifier = Modifier.weight(1f))
                            T("⌄", 14.sp)
                        }
                        DropdownMenu(typeMenu, { typeMenu = false }, containerColor = Fv.Surface) {
                            types.forEach { (v, l) -> DropdownMenuItem(text = { T(l, 13.sp) }, onClick = { type = v; typeMenu = false }) }
                        }
                    }
                }
                FormGroup("Detail") { FvInput(detail, { detail = it; sent = null }, "What's going on?", minLines = 3) }
                FvButton(
                    "Submit",
                    {
                        scope.launch {
                            val device = mapOf("model" to Build.MODEL, "android" to Build.VERSION.RELEASE, "app_version" to BuildConfig.VERSION_NAME)
                            val net = mapOf("internet" to internet, "dispatch" to (serverOk == true), "gps" to gps)
                            sent = when (val r = repo.reportAppIssue(type, detail.trim(), device, net)) {
                                is ApiResult.Ok -> { detail = ""; "Sent to Dispatch. Thanks!" }
                                is ApiResult.Err -> r.message
                                ApiResult.Queued -> null
                            }
                        }
                    },
                    Modifier.fillMaxWidth(), BtnKind.Primary, enabled = detail.isNotBlank(),
                )
                sent?.let { T(it, 12.sp, Fv.Success, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
    }
}

/** .network-status-row: label left, bold Yes / No right, optional meta line under it. */
@Composable
private fun StatusRow(label: String, ok: Boolean?, meta: String?) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            T(label, 13.sp)
            Spacer(Modifier.weight(1f))
            T(when (ok) { true -> "Yes"; false -> "No"; null -> "…" }, 13.sp, if (ok == false) Fv.Danger else Fv.Success, FontWeight.Bold)
        }
        if (meta != null) T(meta, 11.5.sp, Fv.TextFaint, modifier = Modifier.padding(bottom = 8.dp))
        Rule()
    }
}
