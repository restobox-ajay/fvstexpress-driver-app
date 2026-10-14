package com.fvstexpress.driver.ui.failed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.fvstexpress.driver.AppContainer
import com.fvstexpress.driver.R
import com.fvstexpress.driver.data.model.FailureReason
import com.fvstexpress.driver.data.model.StopDetail
import com.fvstexpress.driver.data.repo.ApiResult
import com.fvstexpress.driver.ui.components.AppShell
import com.fvstexpress.driver.ui.components.BottomTab
import com.fvstexpress.driver.ui.components.BtnKind
import com.fvstexpress.driver.ui.components.ConfirmDialog
import com.fvstexpress.driver.ui.components.FormGroup
import com.fvstexpress.driver.ui.components.FormHelp
import com.fvstexpress.driver.ui.components.FvAlert
import com.fvstexpress.driver.ui.components.FvBadge
import com.fvstexpress.driver.ui.components.FvButton
import com.fvstexpress.driver.ui.components.FvInput
import com.fvstexpress.driver.ui.components.H1
import com.fvstexpress.driver.ui.components.H2
import com.fvstexpress.driver.ui.components.OnlineToggle
import com.fvstexpress.driver.ui.components.Rule
import com.fvstexpress.driver.ui.components.T
import com.fvstexpress.driver.ui.components.Tone
import com.fvstexpress.driver.ui.components.TopBar
import com.fvstexpress.driver.ui.components.TopIcon
import com.fvstexpress.driver.ui.components.rememberOnlineToggle
import com.fvstexpress.driver.ui.nav.Routes
import com.fvstexpress.driver.ui.theme.Fv
import kotlinx.coroutines.launch

/** task-detail-failed.html: "Report a problem" — reason, required note, Restart or Return to pool. */
@Composable
fun FailedScreen(nav: NavController, container: AppContainer, stopKey: String, reference: String) {
    val repo = container.repository
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val (online, setOnline) = rememberOnlineToggle(container)
    var stop by remember { mutableStateOf<StopDetail?>(null) }
    var reasons by remember { mutableStateOf<List<FailureReason>>(emptyList()) }
    var reason by remember { mutableStateOf<FailureReason?>(null) }
    var menu by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var confirmStep by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        (repo.stop(stopKey) as? ApiResult.Ok)?.let { stop = it.data }
        when (val r = repo.failureReasons()) {
            is ApiResult.Ok -> reasons = r.data
            is ApiResult.Err -> snackbar.showSnackbar(r.message)
            ApiResult.Queued -> Unit
        }
    }

    fun choose(step: String) {
        when {
            reason == null -> scope.launch { snackbar.showSnackbar("Select a reason first.") }
            note.isBlank() -> scope.launch { snackbar.showSnackbar("Describe what happened in the note.") }
            else -> confirmStep = step
        }
    }

    fun submit(step: String) {
        val code = reason?.code ?: return
        busy = true
        scope.launch {
            when (val r = repo.failed(stopKey, reference, code, note, step)) {
                is ApiResult.Ok, ApiResult.Queued -> nav.navigate(Routes.TASKS) { popUpTo(Routes.TASKS) { inclusive = true } }
                is ApiResult.Err -> snackbar.showSnackbar(r.message)
            }
            busy = false
        }
    }

    AppShell(
        nav = nav, container = container, tab = BottomTab.TASKS, snackbar = snackbar,
        topBar = {
            TopBar(
                left = { TopIcon(R.drawable.ic_arrow_left, "Back") { nav.popBackStack() } },
                center = { OnlineToggle(online, setOnline) },
            )
        },
    ) { modifier ->
        Column(modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
            H1("Report a problem")
            val orderName = stop?.orders?.firstOrNull { it.reference == reference }?.name ?: reference
            val street = stop?.address?.substringBefore(",") ?: ""
            T(if (street.isNotEmpty()) "$orderName — $street" else orderName, 13.sp)
            FvAlert("Marking this Failed stops the delivery attempt. Admin is notified immediately (with a sound alert).", Tone.Warning)

            Column(Modifier.padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    H2("What happened?")
                    FvBadge("Required", Tone.Danger)
                }
                FormGroup("Reason") {
                    Box {
                        val shape = RoundedCornerShape(Fv.RadiusSm)
                        Row(
                            Modifier.fillMaxWidth().clip(shape).background(Fv.SurfaceAlt).border(1.dp, Fv.BorderStrong, shape)
                                .clickable { menu = true }.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            T(reason?.label ?: "Select a reason...", 13.sp, modifier = Modifier.weight(1f))
                            T("⌄", 14.sp)
                        }
                        DropdownMenu(menu, { menu = false }, containerColor = Fv.Surface) {
                            reasons.forEach { r -> DropdownMenuItem(text = { T(r.label, 13.sp) }, onClick = { reason = r; menu = false }) }
                        }
                    }
                    FormHelp("Shared list — same reasons admin sees under Exceptions. Choosing \"Other\" means you must describe it in the note below.")
                }
                FormGroup("Note (required)") { FvInput(note, { note = it }, "Describe what happened...", minLines = 3) }
            }
            Rule()
            Column(Modifier.padding(vertical = 10.dp)) {
                H2("Next step", Modifier.padding(bottom = 8.dp))
                T(
                    "Redelivery pricing (if any) and creating the replacement order happen on the admin side once this is submitted — your job here is just to record what happened.",
                    14.5.sp, Fv.TextMuted, lineHeight = 20.sp, modifier = Modifier.padding(bottom = 8.dp),
                )
                FvButton("Restart this attempt", { choose("restart") }, Modifier.fillMaxWidth(), BtnKind.Primary, busy = busy && confirmStep == null)
                Spacer(Modifier.height(8.dp))
                FvButton("Return to order pool (fully failed)", { choose("return_to_pool") }, Modifier.fillMaxWidth(), BtnKind.Danger, enabled = !busy)
                FormHelp(
                    "\"Restart this attempt\" keeps the order with you — use this for something recoverable (e.g. found a parking spot, re-attempting after a short wait). \"Return to order pool\" ends your attempt entirely — order shows Failed + this reason for dispatch to handle.",
                    Modifier.padding(top = 4.dp),
                )
            }
            Rule()
        }
    }

    confirmStep?.let { step ->
        ConfirmDialog(
            "Confirm status change",
            if (step == "restart") "Restart this attempt? The order stays with you." else "Return this order to the pool as Failed?",
            onConfirm = { confirmStep = null; submit(step) },
            onDismiss = { confirmStep = null },
        )
    }
}
