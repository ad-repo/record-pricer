package com.recordpricer.api

import retrofit2.http.GET
import retrofit2.http.Query

interface EbayApi {
    @GET("services/search/FindingService/v1")
    suspend fun findSoldItems(
        @Query("OPERATION-NAME")           operation: String = "findCompletedItems",
        @Query("SERVICE-VERSION")          version: String = "1.0.0",
        @Query("SECURITY-APPNAME")         appName: String,
        @Query("RESPONSE-DATA-FORMAT")     format: String = "JSON",
        @Query("keywords")                 keywords: String,
        @Query(value = "itemFilter(0).name",  encoded = true) f0name: String = "SoldItemsOnly",
        @Query(value = "itemFilter(0).value", encoded = true) f0value: String = "true",
        @Query(value = "itemFilter(1).name",  encoded = true) f1name: String = "LocatedIn",
        @Query(value = "itemFilter(1).value", encoded = true) f1value: String = "US",
        @Query(value = "itemFilter(2).name",  encoded = true) f2name: String = "Currency",
        @Query(value = "itemFilter(2).value", encoded = true) f2value: String = "USD",
        @Query("sortOrder")                sort: String = "EndTimeSoonest",
        @Query("paginationInput.entriesPerPage") pageSize: Int = 25
    ): EbayFindResponse
}

data class EbayFindResponse(
    val findCompletedItemsResponse: List<EbayResponseBody>?
)
data class EbayResponseBody(
    val searchResult: List<EbaySearchResult>?
)
data class EbaySearchResult(
    val count: String?,
    val item: List<EbayItem>?
)
data class EbayItem(
    val title: List<String>?,
    val sellingStatus: List<EbaySellingStatus>?
)
data class EbaySellingStatus(
    val convertedCurrentPrice: List<EbayPrice>?,
    val sellingState: List<String>?
)
data class EbayPrice(
    val value: String?,
    val currencyId: String? = "USD"
)
