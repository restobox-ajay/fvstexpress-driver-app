package com.fvstexpress.driver

import android.app.Application
import com.fvstexpress.driver.data.api.ApiFactory
import com.fvstexpress.driver.data.api.DriverApi
import com.fvstexpress.driver.data.api.FakeDriverApi
import com.fvstexpress.driver.data.offline.OfflineQueue
import com.fvstexpress.driver.data.offline.PingBacklog
import com.fvstexpress.driver.data.repo.DriverRepository
import com.fvstexpress.driver.data.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** Hand-wired dependencies (small app, no DI framework needed). */
class AppContainer(app: Application) {
    val session = SessionStore(app, ApiFactory.json)
    val authedClient: OkHttpClient by lazy { ApiFactory.okHttp(session) }
    val api: DriverApi by lazy { if (BuildConfig.USE_MOCK_API) FakeDriverApi() else ApiFactory.create(authedClient) }
    val offlineQueue = OfflineQueue(app, ApiFactory.json)
    val pingBacklog = PingBacklog(app, ApiFactory.json)
    val repository by lazy { DriverRepository(api, session, offlineQueue, pingBacklog) }
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** The top-bar Online toggle, shared by every screen. */
    val online = kotlinx.coroutines.flow.MutableStateFlow(false)
}

class FvstApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch {
            container.session.load()
            container.online.value = container.session.session.value?.driver?.online ?: false
        }
        if (container.offlineQueue.pending.value.isNotEmpty()) container.offlineQueue.scheduleSync()
        container.appScope.launch { if (!container.pingBacklog.isEmpty()) container.pingBacklog.scheduleSync() }
    }
}
