package com.fvstexpress.driver.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fvstexpress.driver.AppContainer
import com.fvstexpress.driver.BuildConfig
import com.fvstexpress.driver.data.repo.ApiResult
import com.fvstexpress.driver.ui.components.BtnKind
import com.fvstexpress.driver.ui.components.FormGroup
import com.fvstexpress.driver.ui.components.FormHelp
import com.fvstexpress.driver.ui.components.FvButton
import com.fvstexpress.driver.ui.components.FvInput
import com.fvstexpress.driver.ui.components.H1
import com.fvstexpress.driver.ui.components.T
import com.fvstexpress.driver.ui.theme.Fv
import kotlinx.coroutines.launch

/** login.html: .login-screen — centred brand mark, two form groups, primary block button. */
@Composable
fun LoginScreen(container: AppContainer, onLoggedIn: () -> Unit) {
    var login by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (login.isBlank() || pin.isBlank()) { error = "Enter your phone number or employee card # and your PIN."; return }
        busy = true; error = null
        scope.launch {
            when (val r = container.repository.login(login, pin)) {
                is ApiResult.Ok -> { container.online.value = r.data?.online ?: false; onLoggedIn() }
                is ApiResult.Err -> error = if (r.code == 401) "That login and PIN don't match. Contact Dispatch if you forgot your PIN." else r.message
                ApiResult.Queued -> error = "No connection to Dispatch."
            }
            busy = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Fv.Bg).systemBarsPadding().imePadding()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            // .login-brand
            Column(Modifier.fillMaxWidth().padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Fv.Accent), contentAlignment = Alignment.Center) {
                    T("FV", 18.sp, Color.White, FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(10.dp))
                H1("FVST Express")
                T("Driver", 14.5.sp, Fv.TextFaint, modifier = Modifier.padding(top = 2.dp))
            }

            FormGroup("Phone number or employee card #") {
                FvInput(login, { login = it }, "(604) 555-0110")
            }
            FormGroup("PIN / Password") {
                FvInput(pin, { pin = it.filter(Char::isLetterOrDigit) }, "••••••", password = true, keyboardType = KeyboardType.NumberPassword)
            }
            error?.let { T(it, 12.sp, Fv.Danger, modifier = Modifier.padding(bottom = 10.dp)) }

            FvButton("Log in", { submit() }, Modifier.fillMaxWidth(), BtnKind.Primary, busy = busy)
            FormHelp("Forgot your PIN? Contact dispatch.", Modifier.fillMaxWidth().padding(top = 6.dp), TextAlign.Center)
            if (BuildConfig.USE_MOCK_API) {
                FormHelp("Demo mode — sample data, any login and PIN work.", Modifier.fillMaxWidth().padding(top = 14.dp), TextAlign.Center)
            }
        }
    }
}
