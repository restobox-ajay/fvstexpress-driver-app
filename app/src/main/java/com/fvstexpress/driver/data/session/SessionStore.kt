package com.fvstexpress.driver.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fvstexpress.driver.data.model.DriverDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.sessionDataStore by preferencesDataStore(name = "session")

data class Session(val accessToken: String, val refreshToken: String, val driver: DriverDto?)

/**
 * Tokens + the logged-in driver. Persisted in DataStore and mirrored in memory so the OkHttp
 * interceptor can read the token synchronously.
 */
class SessionStore(private val context: Context, private val json: Json) {
    private val keyAccess = stringPreferencesKey("access_token")
    private val keyRefresh = stringPreferencesKey("refresh_token")
    private val keyDriver = stringPreferencesKey("driver")
    private val keyDeviceId = stringPreferencesKey("device_id")

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    @Volatile var deviceId: String = ""
        private set

    suspend fun load() {
        val prefs = context.sessionDataStore.data.first()
        deviceId = prefs[keyDeviceId] ?: UUID.randomUUID().toString().also { id ->
            context.sessionDataStore.edit { it[keyDeviceId] = id }
        }
        val access = prefs[keyAccess]
        val refresh = prefs[keyRefresh]
        val driver = prefs[keyDriver]?.let { runCatching { json.decodeFromString(DriverDto.serializer(), it) }.getOrNull() }
        _session.value = if (access != null && refresh != null) Session(access, refresh, driver) else null
        _loaded.value = true
    }

    val accessToken: String? get() = _session.value?.accessToken
    val refreshToken: String? get() = _session.value?.refreshToken

    suspend fun save(access: String, refresh: String, driver: DriverDto?) {
        val keep = driver ?: _session.value?.driver
        _session.value = Session(access, refresh, keep)
        context.sessionDataStore.edit {
            it[keyAccess] = access
            it[keyRefresh] = refresh
            if (keep != null) it[keyDriver] = json.encodeToString(DriverDto.serializer(), keep)
        }
    }

    suspend fun updateDriver(driver: DriverDto) {
        val current = _session.value ?: return
        _session.value = current.copy(driver = driver)
        context.sessionDataStore.edit { it[keyDriver] = json.encodeToString(DriverDto.serializer(), driver) }
    }

    suspend fun clear() {
        _session.value = null
        context.sessionDataStore.edit {
            it.remove(keyAccess)
            it.remove(keyRefresh)
            it.remove(keyDriver)
        }
    }
}
