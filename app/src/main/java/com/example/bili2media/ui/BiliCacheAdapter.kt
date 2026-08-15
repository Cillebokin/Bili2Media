package com.example.bili2media.ui

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.bili2media.R
import com.example.bili2media.cache.model.BiliCacheEntry
import com.example.bili2media.cache.model.BiliCacheStatus
import com.example.bili2media.ui.image.CoverImageLoader

class BiliCacheAdapter(
    private val coverImageLoader: CoverImageLoader
) : ListAdapter<BiliCacheEntry, BiliCacheAdapter.CacheViewHolder>(DIFF_CALLBACK) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CacheViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bili_cache, parent, false)
        return CacheViewHolder(view, coverImageLoader)
    }

    override fun onBindViewHolder(holder: CacheViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CacheViewHolder(
        itemView: View,
        private val coverImageLoader: CoverImageLoader
    ) : RecyclerView.ViewHolder(itemView) {
        private val imgCover: ImageView = itemView.findViewById(R.id.imgCacheCover)
        private val txtTitle: TextView = itemView.findViewById(R.id.txtCacheTitle)
        private val txtSubtitle: TextView = itemView.findViewById(R.id.txtCacheSubtitle)
        private val txtStatus: TextView = itemView.findViewById(R.id.txtCacheStatus)
        private val txtDetails: TextView = itemView.findViewById(R.id.txtCacheDetails)
        private val txtIds: TextView = itemView.findViewById(R.id.txtCacheIds)
        private val txtPath: TextView = itemView.findViewById(R.id.txtCachePath)

        fun bind(entry: BiliCacheEntry) {
            val context = itemView.context
            txtTitle.text = entry.title
            txtSubtitle.text = entry.subtitle
            txtSubtitle.visibility = if (entry.subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
            txtDetails.text = context.getString(
                R.string.cache_details_format,
                Formatter.formatShortFileSize(context, entry.totalBytes),
                entry.mediaFileCount
            )

            val identifiers = listOfNotNull(
                entry.avid?.let { "AV$it" },
                entry.cid?.let { "CID$it" }
            ).joinToString(" · ")
            txtIds.text = identifiers
            txtIds.visibility = if (identifiers.isBlank()) View.GONE else View.VISIBLE
            txtPath.text = entry.relativePath

            val statusTextRes = when (entry.status) {
                BiliCacheStatus.AVAILABLE -> R.string.cache_status_available
                BiliCacheStatus.NO_MEDIA -> R.string.cache_status_no_media
                BiliCacheStatus.METADATA_ERROR -> R.string.cache_status_metadata_error
            }
            val statusColorRes = when (entry.status) {
                BiliCacheStatus.METADATA_ERROR -> R.color.bili2media_danger
                else -> R.color.bili2media_text_secondary
            }
            txtStatus.setText(statusTextRes)
            txtStatus.setTextColor(ContextCompat.getColor(context, statusColorRes))
            coverImageLoader.load(imgCover, entry.coverSource)
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<BiliCacheEntry>() {
            override fun areItemsTheSame(oldItem: BiliCacheEntry, newItem: BiliCacheEntry): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: BiliCacheEntry, newItem: BiliCacheEntry): Boolean {
                return oldItem == newItem
            }
        }
    }
}
