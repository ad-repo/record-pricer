package com.recordpricer

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object KeysPrefs {
    private const val FILE = "secure_api_keys"
    const val KEY_VISION      = "google_vision"
    const val KEY_DISCOGS     = "discogs_token"
    const val KEY_EBAY        = "ebay_app_id"
    const val KEY_EBAY_SECRET = "ebay_client_secret"

    private fun prefs(ctx: Context) = EncryptedSharedPreferences.create(
        ctx,
        FILE,
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun vision(ctx: Context)      = prefs(ctx).getString(KEY_VISION,      "") ?: ""
    fun discogs(ctx: Context)     = prefs(ctx).getString(KEY_DISCOGS,     "") ?: ""
    fun ebay(ctx: Context)        = prefs(ctx).getString(KEY_EBAY,        "") ?: ""
    fun ebaySecret(ctx: Context)  = prefs(ctx).getString(KEY_EBAY_SECRET, "") ?: ""

    fun save(ctx: Context, vision: String, discogs: String, ebay: String, ebaySecret: String) {
        prefs(ctx).edit()
            .putString(KEY_VISION,      vision.trim())
            .putString(KEY_DISCOGS,     discogs.trim())
            .putString(KEY_EBAY,        ebay.trim())
            .putString(KEY_EBAY_SECRET, ebaySecret.trim())
            .apply()
    }

    fun hasRequiredKeys(ctx: Context) =
        vision(ctx).isNotBlank() && discogs(ctx).isNotBlank()
}
