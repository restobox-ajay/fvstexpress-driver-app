package com.fvstexpress.driver.data.api

import com.fvstexpress.driver.BuildConfig
import com.fvstexpress.driver.data.model.RefreshRequest
import com.fvstexpress.driver.data.session.SessionStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

object ApiFactory {
    @OptIn(ExperimentalSerializationApi::class)
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    /** Headers every call carries: Host override (WAMP vhost), app version, device id, idempotency key. */
    private class CommonHeaders(private val session: SessionStore) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            val b = req.newBuilder()
                .header("Accept", "application/json")
                .header("X-App-Version", BuildConfig.VERSION_NAME)
                .header("X-Device-Id", session.deviceId)
            if (BuildConfig.API_HOST_HEADER.isNotBlank()) b.header("Host", BuildConfig.API_HOST_HEADER)
            if (req.method != "GET" && req.header("Idempotency-Key") == null) {
                b.header("Idempotency-Key", UUID.randomUUID().toString())
            }
            return chain.proceed(b.build())
        }
    }

    private class BearerAuth(private val session: SessionStore) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val token = session.accessToken
            val req = chain.request()
            if (token == null || req.url.encodedPath.endsWith("/auth/login") || req.url.encodedPath.endsWith("/auth/refresh")) {
                return chain.proceed(req)
            }
            return chain.proceed(req.newBuilder().header("Authorization", "Bearer $token").build())
        }
    }

    /** On 401, swaps the refresh token for a new access token once, then retries the request. */
    private class TokenRefresher(private val session: SessionStore, private val plainApi: () -> DriverApi) : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            if (response.request.url.encodedPath.contains("/auth/")) return null
            if (response.priorResponse != null) return null // already retried once
            val refresh = session.refreshToken ?: return null
            synchronized(this) {
                val current = session.accessToken
                val sent = response.request.header("Authorization")?.removePrefix("Bearer ")
                if (current != null && current != sent) {
                    // Another request refreshed while we waited.
                    return response.request.newBuilder().header("Authorization", "Bearer $current").build()
                }
                val tokens = runCatching { runBlocking { plainApi().refresh(RefreshRequest(refresh)) } }.getOrNull()
                if (tokens == null) {
                    runBlocking { session.clear() }
                    return null
                }
                runBlocking { session.save(tokens.accessToken, tokens.refreshToken, tokens.driver) }
                return response.request.newBuilder().header("Authorization", "Bearer ${tokens.accessToken}").build()
            }
        }
    }

    fun okHttp(session: SessionStore, withAuth: Boolean = true): OkHttpClient {
        val b = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(CommonHeaders(session))
        if (withAuth) {
            b.addInterceptor(BearerAuth(session))
            b.authenticator(TokenRefresher(session) { create(okHttp(session, withAuth = false)) })
        }
        if (BuildConfig.DEBUG) {
            b.addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
                redactHeader("Authorization")
            })
        }
        return b.build()
    }

    fun create(client: OkHttpClient): DriverApi =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(DriverApi::class.java)
}
