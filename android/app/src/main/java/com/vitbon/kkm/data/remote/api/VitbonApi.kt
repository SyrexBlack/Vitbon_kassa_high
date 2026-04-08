package com.vitbon.kkm.data.remote.api

import com.vitbon.kkm.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface VitbonApi {

    // Auth
    @POST("api/v1/auth/login")
    suspend fun login(@Body req: LoginRequestDto): Response<LoginResponseDto>

    @POST("api/v1/auth/logout")
    suspend fun logout(): Response<Unit>

    // Checks
    @POST("api/v1/checks/sync")
    suspend fun syncChecks(@Body req: CheckSyncRequestDto): Response<CheckSyncResponseDto>

    @GET("api/v1/checks")
    suspend fun getChecks(
        @Query("shiftId") shiftId: String?,
        @Query("date") date: String?,
        @Query("since") since: Long?
    ): Response<List<CheckDto>>

    // Products
    @GET("api/v1/products")
    suspend fun getProducts(@Query("since") since: Long?): Response<ProductSyncResponseDto>

    @POST("api/v1/products/sync")
    suspend fun syncProducts(@Body products: List<ProductDto>): Response<Unit>

    // Documents
    @POST("api/v1/documents/acceptance")
    suspend fun sendAcceptance(@Body doc: DocumentDto): Response<Unit>

    @POST("api/v1/documents/writeoff")
    suspend fun sendWriteoff(@Body doc: DocumentDto): Response<Unit>

    @POST("api/v1/documents/inventory")
    suspend fun sendInventory(@Body doc: DocumentDto): Response<Unit>

    // Shifts
    @GET("api/v1/shifts/{cashierId}")
    suspend fun getShifts(@Path("cashierId") cashierId: String): Response<List<ShiftDto>>

    @POST("api/v1/shifts")
    suspend fun openShift(@Body shift: ShiftDto): Response<ShiftDto>

    @PUT("api/v1/shifts/{id}/close")
    suspend fun closeShift(@Path("id") id: String): Response<Unit>

    // Status
    @GET("api/v1/statuses")
    suspend fun getStatuses(): Response<StatusResponseDto>

    // Licensing
    @POST("api/v1/license/check")
    suspend fun checkLicense(@Body req: LicenseCheckRequestDto): Response<LicenseCheckResponseDto>

    // ЕГАИС (optional)
    @POST("api/v1/egais/incoming")
    suspend fun egaisIncoming(@Body payload: String): Response<String>

    @POST("api/v1/egais/tara")
    suspend fun egaisTara(@Body payload: String): Response<String>

    // Честный ЗНАК (optional)
    @POST("api/v1/chaseznak/sell")
    suspend fun chaseznakSell(@Body payload: String): Response<String>

    @POST("api/v1/chaseznak/verify-age")
    suspend fun verifyAge(@Body payload: String): Response<String>
}
