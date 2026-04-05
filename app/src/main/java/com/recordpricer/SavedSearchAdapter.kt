package com.recordpricer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.recordpricer.databinding.ItemSavedSearchBinding
import com.recordpricer.db.SavedSearchEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SavedSearchAdapter(
    private val items: MutableList<SavedSearchEntity>,
    private val onItemClick: (SavedSearchEntity) -> Unit
) : RecyclerView.Adapter<SavedSearchAdapter.VH>() {

    private val dateFmt = SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault())

    inner class VH(val binding: ItemSavedSearchBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemSavedSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = items[position]
        val b = holder.binding

        b.tvTitle.text = entry.queryString
        b.tvQuery.text = dateFmt.format(Date(entry.savedAt))
        b.tvFavStar.text = "▶"

        val photo = entry.photoPath
        if (!photo.isNullOrBlank() && File(photo).exists()) {
            Glide.with(b.ivThumbnail.context).load(File(photo)).centerCrop().into(b.ivThumbnail)
        } else {
            b.ivThumbnail.setImageDrawable(null)
            b.ivThumbnail.setBackgroundColor(0xFF2A2A2A.toInt())
        }

        b.root.setOnClickListener { onItemClick(entry) }
    }

    fun removeAt(position: Int): SavedSearchEntity {
        val removed = items.removeAt(position)
        notifyItemRemoved(position)
        return removed
    }
}
