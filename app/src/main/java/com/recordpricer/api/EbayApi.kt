package com.recordpricer.api

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface EbayAuthApi {
    @FormUrlEncoded
    @POST("identity/v1/oauth2/token")
    suspend fun getToken(
        @Header("Authorization") basicAuth: String,
        @Field("grant_type") grantType: String = "client_credentials",
        @Field("scope") scope: String = "https://api.ebay.com/oauth/api_scope"
    ): EbayTokenResponse
}

data class EbayTokenResponse(
    val access_token: String?,
    val expires_in: Int?
)

interface EbayApi {
    @GET("buy/browse/v1/item_summary/search")
    suspend fun findSoldItems(
        @Header("Authorization") bearer: String,
        @Query("q") keywords: String,
        @Query("filter") filter: String = "itemLocationCountry:US",
        @Query("limit") limit: Int = 25
    ): EbayInsightsResponse
}

data class EbayInsightsResponse(
    val itemSummaries: List<EbayItemSummary>?
)

data class EbayItemSummary(
    val title: String?,
    val price: EbayItemPrice?
)

data class EbayItemPrice(
    val value: String?,
    val currency: String?
)
