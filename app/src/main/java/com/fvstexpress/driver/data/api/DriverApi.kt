package com.fvstexpress.driver.data.api

import com.fvstexpress.driver.data.model.AnalyticsSummary
import com.fvstexpress.driver.data.model.AppIssueRequest
import com.fvstexpress.driver.data.model.DeliveredRequest
import com.fvstexpress.driver.data.model.DriverDto
import com.fvstexpress.driver.data.model.ExceptionRequest
import com.fvstexpress.driver.data.model.FailedRequest
import com.fvstexpress.driver.data.model.FailedResponse
import com.fvstexpress.driver.data.model.FailureReason
import com.fvstexpress.driver.data.model.IdResponse
import com.fvstexpress.driver.data.model.LoginRequest
import com.fvstexpress.driver.data.model.MessagesResponse
import com.fvstexpress.driver.data.model.OrderDetail
import com.fvstexpress.driver.data.model.PhotoResponse
import com.fvstexpress.driver.data.model.PickedUpRequest
import com.fvstexpress.driver.data.model.PickedUpResponse
import com.fvstexpress.driver.data.model.PingResponse
import com.fvstexpress.driver.data.model.PresenceRequest
import com.fvstexpress.driver.data.model.PresenceResponse
import com.fvstexpress.driver.data.model.RefreshRequest
import com.fvstexpress.driver.data.model.RouteResponse
import com.fvstexpress.driver.data.model.StarRequest
import com.fvstexpress.driver.data.model.StatusActionRequest
import com.fvstexpress.driver.data.model.StatusResponse
import com.fvstexpress.driver.data.model.StopDetail
import com.fvstexpress.driver.data.model.StopsResponse
import com.fvstexpress.driver.data.model.TokenResponse
import com.fvstexpress.driver.data.model.UndoRequest
import com.fvstexpress.driver.data.model.WorkRequest
import com.fvstexpress.driver.data.model.WorkResponse
import com.fvstexpress.driver.data.model.WorkToday
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** /driver-api/v1 — same list as the Postman collection and the admin app's DriverApi controllers. */
interface DriverApi {
    // Auth
    @POST("auth/login") suspend fun login(@Body body: LoginRequest): TokenResponse
    @POST("auth/refresh") suspend fun refresh(@Body body: RefreshRequest): TokenResponse
    @POST("auth/logout") suspend fun logout(@Body body: RefreshRequest): Response<Unit>

    // Driver, presence, hours
    @GET("me") suspend fun me(): DriverDto
    @POST("presence") suspend fun presence(@Body body: PresenceRequest): PresenceResponse
    @POST("work") suspend fun work(@Body body: WorkRequest): WorkResponse
    @GET("work/today") suspend fun workToday(): WorkToday

    // Tasks & route
    @GET("stops") suspend fun stops(@Query("view") view: String): StopsResponse
    @GET("route") suspend fun route(@Query("date") date: String): RouteResponse
    @GET("stops/{stopKey}") suspend fun stop(@Path("stopKey") stopKey: String): StopDetail
    @GET("orders/{reference}") suspend fun order(@Path("reference") reference: String): OrderDetail

    // Pickup
    @POST("stops/{stopKey}/picking-up")
    suspend fun pickingUp(@Path("stopKey") stopKey: String, @Body body: StatusActionRequest): StatusResponse

    @POST("stops/{stopKey}/picked-up")
    suspend fun pickedUp(@Path("stopKey") stopKey: String, @Body body: PickedUpRequest): PickedUpResponse

    @POST("stops/{stopKey}/undo")
    suspend fun undo(@Path("stopKey") stopKey: String, @Body body: UndoRequest): StatusResponse

    // Delivery
    @POST("stops/{stopKey}/in-transit")
    suspend fun inTransit(@Path("stopKey") stopKey: String, @Body body: StatusActionRequest): StatusResponse

    @Multipart
    @POST("photos")
    suspend fun uploadPhoto(
        @Part file: MultipartBody.Part,
        @Part("reference") reference: RequestBody,
        @Part("client_photo_id") clientPhotoId: RequestBody,
        @Part("taken_at") takenAt: RequestBody,
        @Part("latitude") latitude: RequestBody?,
        @Part("longitude") longitude: RequestBody?,
    ): PhotoResponse

    @DELETE("photos/{photoId}") suspend fun deletePhoto(@Path("photoId") photoId: String): Response<Unit>

    @POST("stops/{stopKey}/delivered")
    suspend fun delivered(@Path("stopKey") stopKey: String, @Body body: DeliveredRequest): StatusResponse

    // Failed / problems
    @GET("failure-reasons") suspend fun failureReasons(): List<FailureReason>

    @POST("stops/{stopKey}/failed")
    suspend fun failed(@Path("stopKey") stopKey: String, @Body body: FailedRequest): FailedResponse

    @POST("exceptions") suspend fun reportException(@Body body: ExceptionRequest): IdResponse

    // Messages
    @GET("messages") suspend fun messages(): MessagesResponse
    @POST("messages/{id}/read") suspend fun markRead(@Path("id") id: Int): Response<Unit>
    @PUT("messages/{id}/star") suspend fun star(@Path("id") id: Int, @Body body: StarRequest): StarRequest

    // Analytics & connectivity
    @GET("analytics/summary") suspend fun analytics(@Query("range") range: String): AnalyticsSummary
    @GET("ping") suspend fun ping(): PingResponse
    @POST("app-issues") suspend fun appIssue(@Body body: AppIssueRequest): IdResponse
}
