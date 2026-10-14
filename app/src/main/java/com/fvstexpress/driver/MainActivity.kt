package com.fvstexpress.driver

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.fvstexpress.driver.ui.nav.AppNav
import com.fvstexpress.driver.ui.theme.FvstTheme

class MainActivity : ComponentActivity() {
    private val localNetworkPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        requestLocalNetworkIfNeeded()
        val container = (application as FvstApp).container
        setContent {
            FvstTheme {
                AppNav(container)
            }
        }
    }

    /**
     * Android 17+ blocks LAN connections unless ACCESS_LOCAL_NETWORK is granted. Only asked when the
     * API server is on the local network (dev WAMP / LAN IP), never for the live HTTPS server.
     */
    private fun requestLocalNetworkIfNeeded() {
        if (Build.VERSION.SDK_INT < 37 || BuildConfig.USE_MOCK_API || !isLocalHost(Uri.parse(BuildConfig.API_BASE_URL).host)) return
        if (ContextCompat.checkSelfPermission(this, LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED) return
        localNetworkPermission.launch(LOCAL_NETWORK)
    }

    private fun isLocalHost(host: String?): Boolean {
        if (host == null) return false
        if (host.endsWith(".local") || host == "localhost") return true
        val p = host.split('.').mapNotNull { it.toIntOrNull() }
        if (p.size != 4) return false
        return p[0] == 10 || p[0] == 127 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] in 16..31)
    }

    private companion object {
        const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
