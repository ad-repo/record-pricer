package com.recordpricer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.recordpricer.databinding.ActivitySavedSearchesBinding
import com.recordpricer.db.AppDatabase
import kotlinx.coroutines.launch

class SavedSearchesActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySavedSearchesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySavedSearchesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "History"

        binding.rvSavedSearches.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            val db = AppDatabase.get(this@SavedSearchesActivity)
            val searches = db.savedSearchDao().getAll()

            val adapter = SavedSearchAdapter(
                searches.toMutableList(),
                onItemClick = { entry ->
                    startActivity(
                        Intent(this@SavedSearchesActivity, SearchResultsActivity::class.java)
                            .putExtra(SearchResultsActivity.EXTRA_SEARCH_ID, entry.id)
                    )
                }
            )
            binding.rvSavedSearches.adapter = adapter

            ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
                override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val removed = adapter.removeAt(viewHolder.bindingAdapterPosition)
                    lifecycleScope.launch { db.savedSearchDao().deleteById(removed.id) }
                }
            }).attachToRecyclerView(binding.rvSavedSearches)
        }
    }
}
