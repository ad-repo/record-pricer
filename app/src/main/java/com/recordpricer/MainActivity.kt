package com.recordpricer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.recordpricer.api.AnnotateRequest
import com.recordpricer.api.DiscogsApi
import com.recordpricer.api.DiscogsRelease
import com.recordpricer.api.EbayApi
import com.recordpricer.api.EbayAuthApi
import com.recordpricer.api.VisionApi
import com.recordpricer.api.VisionFeature
import com.recordpricer.api.VisionImage
import com.recordpricer.api.VisionRequest
import com.recordpricer.databinding.ActivityMainBinding
import com.recordpricer.db.AppDatabase
import com.recordpricer.db.FavoriteEntity
import com.recordpricer.db.SavedSearchEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var lastEbayQuery = ""
    private var currentPhotoPath: String? = null
    private var currentSearchQuery: String = ""
    private var lastTopRelease: DiscogsRelease? = null
    private var lastCandidates: List<DiscogsRelease> = emptyList()

    private val visionApi: VisionApi by lazy {
        Retrofit.Builder().baseUrl("https://vision.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create()).build().create(VisionApi::class.java)
    }
    private val discogsApi: DiscogsApi by lazy {
        Retrofit.Builder().baseUrl("https://api.discogs.com/")
            .addConverterFactory(GsonConverterFactory.create()).build().create(DiscogsApi::class.java)
    }
    private val ebayRetrofit by lazy {
        Retrofit.Builder().baseUrl("https://api.ebay.com/")
            .addConverterFactory(GsonConverterFactory.create()).build()
    }
    private val ebayAuthApi: EbayAuthApi by lazy { ebayRetrofit.create(EbayAuthApi::class.java) }
    private val ebayApi: EbayApi by lazy { ebayRetrofit.create(EbayApi::class.java) }
    private var ebayTokenCache: Pair<String, Long>? = null  // token → expiry ms

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val barcode = data.getStringExtra(CameraActivity.EXTRA_BARCODE)
        val photoPath = data.getStringExtra(CameraActivity.EXTRA_PHOTO_PATH)
        when {
            barcode != null -> lookupByBarcode(barcode)
            photoPath != null -> {
                binding.imagePreview.setImageURI(Uri.fromFile(File(photoPath)))
                binding.imagePreview.visibility = View.VISIBLE
                analyzePhoto(photoPath)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnScan.setOnClickListener {
            cameraLauncher.launch(Intent(this, CameraActivity::class.java))
        }
        binding.btnToggleManual.setOnClickListener {
            val show = binding.manualSearchContainer.visibility == View.GONE
            binding.manualSearchContainer.visibility = if (show) View.VISIBLE else View.GONE
            binding.btnToggleManual.backgroundTintList =
                android.content.res.ColorStateList.valueOf(if (show) 0xFF4A3A6A.toInt() else 0xFF2C2C2C.toInt())
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnManualSearch.setOnClickListener { runManualSearch() }
        binding.etAlbum.setOnEditorActionListener { _, _, _ -> runManualSearch(); true }

        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, SavedSearchesActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            if (!KeysPrefs.hasRequiredKeys(this)) {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        } catch (e: Exception) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun runManualSearch() {
        val artist = binding.etArtist.text?.toString()?.trim() ?: ""
        val album  = binding.etAlbum.text?.toString()?.trim() ?: ""
        val query = listOf(artist, album).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return
        // Hide keyboard
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.etAlbum.windowToken, 0)
        lastEbayQuery = query
        currentSearchQuery = query
        currentPhotoPath = null
        binding.etArtist.text?.clear()
        binding.etAlbum.text?.clear()
        reset(); setStatus("Searching...")
        lifecycleScope.launch {
            try {
                val results = discogsApi.search(query = query, token = KeysPrefs.discogs(this@MainActivity))
                    .results?.let { vinylOnly(it) } ?: emptyList()
                showCandidates(results)
            } catch (e: Exception) {
                Log.e("RecordPricer", "Manual search error", e)
                setStatus("Search failed: ${e.message}"); setLoading(false)
            }
        }
    }

    // ── Barcode path ─────────────────────────────────────────────────────────────
    private fun lookupByBarcode(barcode: String) {
        reset(); setStatus("Looking up barcode...")
        lifecycleScope.launch {
            try {
                val results = discogsApi.search(query = barcode, type = "release", token = KeysPrefs.discogs(this@MainActivity))
                    .results?.let { vinylOnly(it) } ?: emptyList()
                lastEbayQuery = barcode
                currentSearchQuery = barcode
                currentPhotoPath = null
                showCandidates(results)
            } catch (e: Exception) {
                Log.e("RecordPricer", "Barcode error", e)
                setStatus("Lookup failed: ${e.message}"); setLoading(false)
            }
        }
    }

    // ── Photo path ───────────────────────────────────────────────────────────────
    private fun analyzePhoto(photoPath: String) {
        reset(); setStatus("Identifying album...")
        lifecycleScope.launch {
            try {
                val query = identifyAlbum(photoPath)
                if (query.isNullOrBlank()) {
                    setStatus("Could not identify — enter manually below")
                    binding.manualSearchContainer.visibility = View.VISIBLE
                    setLoading(false); return@launch
                }
                setStatus("Searching Discogs...")
                lastEbayQuery = query
                currentSearchQuery = query
                currentPhotoPath = photoPath
                val results = discogsApi.search(query = query, token = KeysPrefs.discogs(this@MainActivity))
                    .results?.let { vinylOnly(it) } ?: emptyList()
                showCandidates(results)
            } catch (e: Exception) {
                Log.e("RecordPricer", "Analyze error", e)
                setStatus("Error: ${e.message}"); setLoading(false)
            }
        }
    }

    // ── Filter to vinyl LPs only, ranked by owners ───────────────────────────────
    private fun vinylOnly(results: List<DiscogsRelease>): List<DiscogsRelease> {
        val nonVinyl = setOf("cd", "cassette", "digital", "file", "dvd", "blu-ray", "sacd", "vhs")
        return results
            .filter { release ->
                val formats = release.format?.map { it.lowercase() } ?: emptyList()
                // Exclude anything with a non-vinyl format
                formats.none { f -> nonVinyl.any { bad -> f.contains(bad) } }
            }
            .sortedWith(compareByDescending<DiscogsRelease> { r ->
                // LP/Album scores highest, other vinyl lower
                val formats = r.format?.map { it.lowercase() } ?: emptyList()
                when {
                    formats.any { it.contains("lp") || it.contains("album") } -> 2
                    formats.any { it.contains("vinyl") }                       -> 1
                    else                                                        -> 0
                }
            }.thenByDescending { it.community?.have ?: 0 })
    }

    // ── Build ranked candidate cards ─────────────────────────────────────────────
    private fun showCandidates(candidates: List<DiscogsRelease>) {
        setLoading(false); setStatus("")
        binding.candidateContainer.removeAllViews()
        binding.manualSearchContainer.visibility = View.GONE
        binding.btnToggleManual.backgroundTintList =
            android.content.res.ColorStateList.valueOf(0xFF2C2C2C.toInt())
        if (candidates.isEmpty()) { setStatus("No Discogs matches found"); return }

        lastTopRelease = candidates.firstOrNull()
        lastCandidates = candidates
        candidates.forEachIndexed { i, release ->
            binding.candidateContainer.addView(buildCandidateCard(release, i == 0))
        }
        binding.candidateContainer.visibility = View.VISIBLE

        // Auto-save every search with full result data
        val querySnapshot = currentSearchQuery
        val photoSnapshot = currentPhotoPath
        val top = candidates.firstOrNull()
        lifecycleScope.launch {
            AppDatabase.get(this@MainActivity).savedSearchDao().insert(
                SavedSearchEntity(
                    queryString  = querySnapshot,
                    photoPath    = photoSnapshot,
                    topTitle     = top?.title,
                    topDiscogsId = top?.id,
                    topUri       = top?.uri,
                    resultsJson  = Gson().toJson(candidates)
                )
            )
        }
    }

    private fun buildCandidateCard(release: DiscogsRelease, isBest: Boolean): CardView {
        val card = CardView(this).apply {
            radius = 8f
            setCardBackgroundColor(0xFF1E1E1E.toInt())
            cardElevation = 4f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dp }
        }

        val inner = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 12.dp, 16.dp, 12.dp)
        }

        // Header row: title (bold) + star toggle
        val btnStar = TextView(this).apply {
            text = "☆"
            textSize = 20f
            setTextColor(0xFFFFAA00.toInt())
            setPadding(8.dp, 0, 0, 0)
        }
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(TextView(this@MainActivity).apply {
                text = release.title ?: "Unknown"
                textSize = 15f
                setTextColor(0xFFFFFFFF.toInt())
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(btnStar)
        }
        inner.addView(headerRow)

        val releaseId = release.id
        if (releaseId != null) {
            lifecycleScope.launch {
                val db = AppDatabase.get(this@MainActivity)
                val isFav = db.favoriteDao().isFavorite(releaseId) > 0
                btnStar.text = if (isFav) "★" else "☆"
                btnStar.setOnClickListener {
                    lifecycleScope.launch {
                        if (db.favoriteDao().isFavorite(releaseId) > 0) {
                            db.favoriteDao().deleteById(releaseId)
                            btnStar.text = "☆"
                        } else {
                            db.favoriteDao().insert(FavoriteEntity(releaseId, release.title ?: "Unknown"))
                            btnStar.text = "★"
                        }
                    }
                }
            }
        }

        // Edition details line
        val details = buildEditionDetails(release)
        if (details.isNotBlank()) {
            inner.addView(TextView(this).apply {
                text = details
                textSize = 13f
                setTextColor(0xFFAAAAAA.toInt())
                setPadding(0, 2.dp, 0, 0)
            })
        }

        // Owners count + best match tag
        inner.addView(TextView(this).apply {
            val have = release.community?.have ?: 0
            text = "%,d owners".format(have) + if (isBest) "  ·  best match" else ""
            textSize = 12f
            setTextColor(if (isBest) 0xFF03DAC6.toInt() else 0xFF666666.toInt())
            setPadding(0, 4.dp, 0, 0)
        })

        // Discogs link
        val discogsUrl = if (!release.uri.isNullOrBlank())
            "https://www.discogs.com${release.uri}"
        else
            "https://www.discogs.com/release/${release.id}"
        inner.addView(TextView(this).apply {
            val label = "Open on Discogs ↗"
            val span = SpannableString(label)
            span.setSpan(object : ClickableSpan() {
                override fun onClick(v: View) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(discogsUrl)))
                }
                override fun updateDrawState(ds: android.text.TextPaint) {
                    ds.color = 0xFF8888FF.toInt()
                    ds.isUnderlineText = true
                }
            }, 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setText(span)
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = Color.TRANSPARENT
            textSize = 12f
            setPadding(0, 6.dp, 0, 0)
        })

        // Pricing section — hidden until tapped
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { topMargin = 10.dp; bottomMargin = 10.dp }
            setBackgroundColor(0xFF333333.toInt())
            visibility = View.GONE
        }
        val pricingBar = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(56.dp, 56.dp)
            visibility = View.GONE
        }
        val tvDiscogs = TextView(this).apply {
            textSize = 14f; setTextColor(0xFF03DAC6.toInt()); visibility = View.GONE
        }
        val tvEbay = TextView(this).apply {
            textSize = 14f; setTextColor(0xFFCF6679.toInt()); setPadding(0, 6.dp, 0, 0); visibility = View.GONE
        }
        val tvEbayLink = TextView(this).apply {
            textSize = 12f; setPadding(0, 4.dp, 0, 0); visibility = View.GONE
        }

        inner.addView(divider)
        inner.addView(pricingBar)
        inner.addView(tvDiscogs)
        inner.addView(tvEbay)
        inner.addView(tvEbayLink)
        card.addView(inner)

        card.setOnClickListener {
            val id = release.id ?: return@setOnClickListener
            // Already showing pricing — collapse
            if (tvDiscogs.visibility == View.VISIBLE || pricingBar.visibility == View.VISIBLE) {
                divider.visibility = View.GONE
                pricingBar.visibility = View.GONE
                tvDiscogs.visibility = View.GONE
                tvEbay.visibility = View.GONE
                tvEbayLink.visibility = View.GONE
                card.setCardBackgroundColor(0xFF1E1E1E.toInt())
                return@setOnClickListener
            }
            // Expand and fetch
            card.setCardBackgroundColor(0xFF1E2A1E.toInt())
            divider.visibility = View.VISIBLE
            pricingBar.visibility = View.VISIBLE
            tvDiscogs.visibility = View.GONE
            tvEbay.visibility = View.GONE

            lifecycleScope.launch {
                val statsDeferred = async { runCatching { discogsApi.marketplaceStats(id, KeysPrefs.discogs(this@MainActivity)) }.getOrNull() }
                val ebayDeferred  = async { fetchEbaySoldPrices(lastEbayQuery) }
                val stats = statsDeferred.await()
                val ebay  = ebayDeferred.await()

                pricingBar.visibility = View.GONE
                val forSale = stats?.num_for_sale ?: 0
                tvDiscogs.text = if (forSale > 0 && stats?.lowest_price?.value != null)
                    "Discogs: from \$%.2f  ($forSale for sale)".format(stats.lowest_price.value)
                else "Discogs: no current listings"
                tvEbay.text = formatEbayResult(ebay)

                val ebayUrl = "https://www.ebay.com/sch/i.html?_nkw=${Uri.encode("$lastEbayQuery vinyl")}&LH_ItemCondition=3000"
                val label = "Search on eBay ↗"
                val span = SpannableString(label)
                span.setSpan(object : ClickableSpan() {
                    override fun onClick(v: View) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ebayUrl))) }
                    override fun updateDrawState(ds: android.text.TextPaint) { ds.color = 0xFF8888FF.toInt(); ds.isUnderlineText = true }
                }, 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                tvEbayLink.setText(span)
                tvEbayLink.movementMethod = LinkMovementMethod.getInstance()
                tvEbayLink.highlightColor = Color.TRANSPARENT

                tvDiscogs.visibility = View.VISIBLE
                tvEbay.visibility = View.VISIBLE
                tvEbayLink.visibility = View.VISIBLE
            }
        }

        return card
    }

    private fun buildEditionDetails(r: DiscogsRelease): String {
        val parts = mutableListOf<String>()
        r.year?.takeIf { it.isNotBlank() }?.let { parts += it }
        r.country?.takeIf { it.isNotBlank() }?.let { parts += it }
        r.label?.firstOrNull()?.takeIf { it.isNotBlank() && it != "Not On Label" }?.let { parts += it }
        val formats = r.format?.filter { it.isNotBlank() }?.take(3)?.joinToString(", ")
        if (!formats.isNullOrBlank()) parts += formats
        r.catno?.takeIf { it.isNotBlank() && it != "none" }?.let { parts += it }
        return parts.joinToString("  ·  ")
    }

    // ── Vision identification ────────────────────────────────────────────────────
    private suspend fun identifyAlbum(photoPath: String): String? {
        val base64 = fileToBase64(photoPath) ?: return null
        val web = visionApi.annotate(KeysPrefs.vision(this),
            VisionRequest(listOf(AnnotateRequest(VisionImage(base64), listOf(VisionFeature("WEB_DETECTION", 10)))))
        ).responses?.firstOrNull()?.webDetection

        web?.pagesWithMatchingImages?.forEach { Log.d("RecordPricer", "page: ${it.pageTitle} | ${it.url}") }
        web?.webEntities?.forEach { Log.d("RecordPricer", "entity: score=${it.score} desc=${it.description}") }

        val musicDomains = listOf("discogs.com", "allmusic.com", "musicbrainz.org", "rateyourmusic.com")
        val pageTitle = web?.pagesWithMatchingImages
            ?.firstOrNull { page -> musicDomains.any { page.url?.contains(it) == true } }?.pageTitle
        if (!pageTitle.isNullOrBlank()) {
            val cleaned = pageTitle
                .replace(Regex("\\s*[-|]\\s*(eBay|Discogs|AllMusic|Wikipedia|MusicBrainz|RateYourMusic|Amazon).*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s*\\|.*$"), "")
                .replace(Regex("\\s*(Vinyl|LP|Record|Remastered|\\(.*\\))\\s*$", RegexOption.IGNORE_CASE), "")
                .trim()
            if (cleaned.length >= 4) { Log.d("RecordPricer", "Page title: $cleaned"); return cleaned }
        }

        val genericTerms = setOf("phonograph record", "album", "double album", "album cover",
            "vinyl record", "lp", "record", "music", "rock music", "hard rock", "phonograph", "compact disc", "single")
        val goodEntities = web?.webEntities
            ?.filter { e -> val d = e.description?.lowercase() ?: return@filter false
                (e.score ?: 0f) > 0.5f && d !in genericTerms && e.description.length > 3 }
            ?.sortedByDescending { it.score } ?: emptyList()

        val artistAlbum = goodEntities.firstOrNull { it.description?.contains(" - ") == true }
        if (artistAlbum != null) {
            val q = (artistAlbum.description ?: "")
                .replace(Regex("\\s*(Vinyl|LP|Record|Remastered|\\(.*\\))\\s*$", RegexOption.IGNORE_CASE), "").trim()
            Log.d("RecordPricer", "Entity: $q"); return q
        }
        return goodEntities.take(2).mapNotNull { it.description }.joinToString(" ").takeIf { it.isNotBlank() }
    }

    // ── eBay sold prices ─────────────────────────────────────────────────────────
    private suspend fun fetchEbaySoldPrices(query: String): EbayResult {
        val clientId     = KeysPrefs.ebay(this)
        val clientSecret = KeysPrefs.ebaySecret(this)
        if (clientId.isBlank() || clientSecret.isBlank())
            return EbayResult(error = "No eBay credentials — add Client ID + Secret in ⚙ Settings")
        return try {
            val token = getEbayToken(clientId, clientSecret)
                ?: return EbayResult(error = "eBay auth failed — check Client ID/Secret")
            val prices = ebayApi.findSoldItems(bearer = "Bearer $token", keywords = "$query vinyl")
                .itemSummaries
                ?.mapNotNull { it.price?.value?.toDoubleOrNull() }
                ?: emptyList()
            if (prices.isEmpty()) EbayResult(count = 0)
            else EbayResult(min = prices.min(), max = prices.max(), avg = prices.average(), count = prices.size)
        } catch (e: retrofit2.HttpException) {
            val body = e.response()?.errorBody()?.string() ?: e.message
            Log.e("RecordPricer", "eBay HTTP ${e.code()}: $body", e)
            EbayResult(error = "eBay ${e.code()}: $body")
        } catch (e: Exception) { Log.e("RecordPricer", "eBay error", e); EbayResult(error = e.message) }
    }

    private suspend fun getEbayToken(clientId: String, clientSecret: String): String? {
        val now = System.currentTimeMillis()
        ebayTokenCache?.let { (token, expiry) -> if (now < expiry) return token }
        val credentials = Base64.encodeToString("$clientId:$clientSecret".toByteArray(), Base64.NO_WRAP)
        val response = ebayAuthApi.getToken("Basic $credentials")
        val token = response.access_token ?: return null
        ebayTokenCache = Pair(token, now + ((response.expires_in ?: 7200) - 300) * 1000L)
        return token
    }

    private fun formatEbayResult(r: EbayResult) = when {
        r.error != null -> "eBay: ${r.error}"
        r.count == 0   -> "eBay: no US listings found"
        else -> "eBay listings (USA): \$%.2f – \$%.2f  avg \$%.2f  (%d listings)".format(r.min, r.max, r.avg, r.count)
    }

    private fun reset() {
        setLoading(true)
        binding.candidateContainer.visibility = View.GONE
        binding.candidateContainer.removeAllViews()
        binding.manualSearchContainer.visibility = View.GONE
        binding.btnToggleManual.backgroundTintList =
            android.content.res.ColorStateList.valueOf(0xFF2C2C2C.toInt())
        lastTopRelease = null
        lastCandidates = emptyList()
    }

    private fun setLoading(on: Boolean) { binding.progressBar.visibility = if (on) View.VISIBLE else View.GONE }
    private fun setStatus(msg: String)   { binding.tvStatus.text = msg }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
}

data class EbayResult(val min: Double? = null, val max: Double? = null,
    val avg: Double? = null, val count: Int = 0, val error: String? = null)

private fun fileToBase64(path: String): String? {
    return try {
        val original = BitmapFactory.decodeFile(path) ?: return null
        val scale = 1200f / maxOf(original.width, original.height)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(original,
            (original.width * scale).toInt(), (original.height * scale).toInt(), true) else original
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) { Log.e("RecordPricer", "Base64 error", e); null }
}
