package com.recordpricer.api

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface DiscogsApi {
    @GET("database/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("type") type: String = "release",
        @Query("format") format: String = "Vinyl",
        @Query("per_page") perPage: Int = 100,
        @Query("token") token: String
    ): DiscogsSearchResponse

    @GET("marketplace/stats/{releaseId}")
    suspend fun marketplaceStats(
        @Path("releaseId") releaseId: Int,
        @Query("token") token: String
    ): DiscogsStatsResponse
}

data class DiscogsSearchResponse(val results: List<DiscogsRelease>?)
data class DiscogsRelease(
    val id: Int?,
    val title: String?,
    val year: String?,
    val country: String?,
    val format: List<String>?,
    val label: List<String>?,
    val catno: String?,
    val uri: String?,
    val community: DiscogsCommunity?
)
data class DiscogsCommunity(val have: Int?, val want: Int?)

data class DiscogsStatsResponse(
    val lowest_price: DiscogsPrice?,
    val num_for_sale: Int?
)
data class DiscogsPrice(val value: Double?, val currency: String?)
