package com.recordpricer

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.recordpricer.api.DiscogsApi
import com.recordpricer.api.DiscogsRelease
import com.recordpricer.databinding.ActivitySearchResultsBinding
import com.recordpricer.db.AppDatabase
import com.recordpricer.db.FavoriteEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

class SearchResultsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SEARCH_ID = "search_id"
    }

    private lateinit var binding: ActivitySearchResultsBinding
    private var queryString = ""

    private val discogsApi: DiscogsApi by lazy {
        Retrofit.Builder().baseUrl("https://api.discogs.com/")
            .addConverterFactory(GsonConverterFactory.create()).build().create(DiscogsApi::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val searchId = intent.getIntExtra(EXTRA_SEARCH_ID, -1)
        if (searchId == -1) { finish(); return }

        lifecycleScope.launch {
            val entity = AppDatabase.get(this@SearchResultsActivity)
                .savedSearchDao().getById(searchId) ?: run { finish(); return@launch }

            queryString = entity.queryString
            supportActionBar?.title = entity.queryString

            // Show photo if available
            val photo = entity.photoPath
            if (!photo.isNullOrBlank() && File(photo).exists()) {
                binding.imagePreview.setImageURI(Uri.fromFile(File(photo)))
                binding.imagePreview.visibility = View.VISIBLE
            }

            // Parse stored results
            val type = object : TypeToken<List<DiscogsRelease>>() {}.type
            val releases: List<DiscogsRelease> = try {
                Gson().fromJson(entity.resultsJson, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }

            releases.forEachIndexed { i, release ->
                binding.candidateContainer.addView(buildCard(release, i == 0))
            }
        }
    }

    private fun buildCard(release: DiscogsRelease, isBest: Boolean): CardView {
        val card = CardView(this).apply {
            radius = 8f
            setCardBackgroundColor(0xFF1E1E1E.toInt())
            cardElevation = 4f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dp }
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 12.dp, 16.dp, 12.dp)
        }

        // Header row: title + star toggle
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
            addView(TextView(this@SearchResultsActivity).apply {
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
                val db = AppDatabase.get(this@SearchResultsActivity)
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

        // Edition details
        val details = buildEditionDetails(release)
        if (details.isNotBlank()) {
            inner.addView(TextView(this).apply {
                text = details
                textSize = 13f
                setTextColor(0xFFAAAAAA.toInt())
                setPadding(0, 2.dp, 0, 0)
            })
        }

        // Owners + best match
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
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                .apply { topMargin = 10.dp; bottomMargin = 10.dp }
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
            if (tvDiscogs.visibility == View.VISIBLE || pricingBar.visibility == View.VISIBLE) {
                divider.visibility = View.GONE
                pricingBar.visibility = View.GONE
                tvDiscogs.visibility = View.GONE
                tvEbay.visibility = View.GONE
                tvEbayLink.visibility = View.GONE
                card.setCardBackgroundColor(0xFF1E1E1E.toInt())
                return@setOnClickListener
            }
            card.setCardBackgroundColor(0xFF1E2A1E.toInt())
            divider.visibility = View.VISIBLE
            pricingBar.visibility = View.VISIBLE
            tvDiscogs.visibility = View.GONE
            tvEbay.visibility = View.GONE
            tvEbayLink.visibility = View.GONE

            val querySnapshot = queryString
            lifecycleScope.launch {
                val statsDeferred = async { runCatching { discogsApi.marketplaceStats(id, KeysPrefs.discogs(this@SearchResultsActivity)) }.getOrNull() }
                val ebayDeferred  = async { EbayRepository.fetchPrices(this@SearchResultsActivity, querySnapshot) }
                val stats = statsDeferred.await()
                val ebay  = ebayDeferred.await()

                pricingBar.visibility = View.GONE
                val forSale = stats?.num_for_sale ?: 0
                tvDiscogs.text = if (forSale > 0 && stats?.lowest_price?.value != null)
                    "Discogs: from \$%.2f  ($forSale for sale)".format(stats.lowest_price.value)
                else "Discogs: no current listings"
                tvEbay.text = EbayRepository.formatResult(ebay)

                val ebayUrl = "https://www.ebay.com/sch/i.html?_nkw=${Uri.encode("$queryString vinyl")}&LH_ItemCondition=3000"
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

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
}
