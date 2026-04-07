package com.recordpricer

import android.content.Context
import android.util.Base64
import android.util.Log
import com.recordpricer.api.EbayApi
import com.recordpricer.api.EbayAuthApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object EbayRepository {

    private val retrofit by lazy {
        Retrofit.Builder().baseUrl("https://api.ebay.com/")
            .addConverterFactory(GsonConverterFactory.create()).build()
    }
    private val authApi by lazy { retrofit.create(EbayAuthApi::class.java) }
    private val api     by lazy { retrofit.create(EbayApi::class.java) }
    private var tokenCache: Pair<String, Long>? = null  // token → expiry ms

    suspend fun fetchPrices(ctx: Context, query: String, formatSuffix: String = "vinyl"): EbayResult {
        val clientId     = KeysPrefs.ebay(ctx)
        val clientSecret = KeysPrefs.ebaySecret(ctx)
        if (clientId.isBlank() || clientSecret.isBlank())
            return EbayResult(error = "No eBay credentials — add Client ID + Secret in ⚙ Settings")
        return try {
            val token = getToken(clientId, clientSecret)
                ?: return EbayResult(error = "eBay auth failed — check Client ID/Secret")
            val prices = api.findSoldItems(bearer = "Bearer $token", keywords = "$query $formatSuffix")
                .itemSummaries
                ?.mapNotNull { it.price?.value?.toDoubleOrNull() }
                ?: emptyList()
            if (prices.isEmpty()) EbayResult(count = 0)
            else EbayResult(min = prices.min(), max = prices.max(), avg = prices.average(), count = prices.size)
        } catch (e: retrofit2.HttpException) {
            val body = e.response()?.errorBody()?.string() ?: e.message
            Log.e("RecordPricer", "eBay HTTP ${e.code()}: $body", e)
            EbayResult(error = "eBay ${e.code()}: $body")
        } catch (e: Exception) {
            Log.e("RecordPricer", "eBay error", e)
            EbayResult(error = e.message)
        }
    }

    private suspend fun getToken(clientId: String, clientSecret: String): String? {
        val now = System.currentTimeMillis()
        tokenCache?.let { (token, expiry) -> if (now < expiry) return token }
        val credentials = Base64.encodeToString("$clientId:$clientSecret".toByteArray(), Base64.NO_WRAP)
        val response = authApi.getToken("Basic $credentials")
        val token = response.access_token ?: return null
        tokenCache = Pair(token, now + ((response.expires_in ?: 7200) - 300) * 1000L)
        return token
    }

    fun formatResult(r: EbayResult) = when {
        r.error != null -> "eBay: ${r.error}"
        r.count == 0   -> "eBay: no US listings found"
        else -> "eBay listings (USA): \$%.2f – \$%.2f  avg \$%.2f  (%d listings)".format(r.min, r.max, r.avg, r.count)
    }
}
