package com.recordpricer

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.recordpricer.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "API Keys"

        // Pre-fill saved values
        binding.etVisionKey.setText(KeysPrefs.vision(this))
        binding.etDiscogsToken.setText(KeysPrefs.discogs(this))
        binding.etEbayAppId.setText(KeysPrefs.ebay(this))

        binding.btnSaveKeys.setOnClickListener {
            val vision  = binding.etVisionKey.text?.toString() ?: ""
            val discogs = binding.etDiscogsToken.text?.toString() ?: ""
            val ebay    = binding.etEbayAppId.text?.toString() ?: ""
            KeysPrefs.save(this, vision, discogs, ebay)
            Toast.makeText(this, "Keys saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
