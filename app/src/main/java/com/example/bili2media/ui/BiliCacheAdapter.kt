package com.example.bili2media.ui

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.bili2media.R
import com.example.bili2media.cache.model.BiliCacheEntry
import com.example.bili2media.cache.model.BiliCacheStatus
import com.example.bili2media.ui.export.BiliCacheListItem
import com.example.bili2media.ui.image.CoverImageLoader

class BiliCacheAdapter(
    private val coverImageLoader: CoverImageLoader,
    private val onExport: (BiliCacheEntry) -> Unit,
    private val onM4aExport: (BiliCacheEntry) -> Unit,
    private val onMp3Export: (BiliCacheEntry) -> Unit
) : ListAdapter<BiliCacheListItem, BiliCacheAdapter.CacheViewHolder>(DIFF_CALLBACK) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CacheViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bili_cache, parent, false)
        return CacheViewHolder(view, coverImageLoader, onExport, onM4aExport, onMp3Export)
    }

    override fun onBindViewHolder(holder: CacheViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CacheViewHolder(
        itemView: View,
        private val coverImageLoader: CoverImageLoader,
        private val onExport: (BiliCacheEntry) -> Unit,
        private val onM4aExport: (BiliCacheEntry) -> Unit,
        private val onMp3Export: (BiliCacheEntry) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val imgCover: ImageView = itemView.findViewById(R.id.imgCacheCover)
        private val txtTitle: TextView = itemView.findViewById(R.id.txtCacheTitle)
        private val txtSubtitle: TextView = itemView.findViewById(R.id.txtCacheSubtitle)
        private val txtDetails: TextView = itemView.findViewById(R.id.txtCacheDetails)
        private val txtStatus: TextView = itemView.findViewById(R.id.txtCacheStatus)
        private val txtPath: TextView = itemView.findViewById(R.id.txtCachePath)
        private val btnExportAction: Button = itemView.findViewById(R.id.btnExportAction)
        private val btnM4aExportAction: Button = itemView.findViewById(R.id.btnM4aExportAction)
        private val btnMp3ExportAction: Button = itemView.findViewById(R.id.btnMp3ExportAction)

        fun bind(item: BiliCacheListItem) {
            val entry = item.entry
            val context = itemView.context
            txtTitle.text = entry.title
            txtSubtitle.text = entry.subtitle
            txtSubtitle.visibility = if (entry.subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
            txtDetails.text = Formatter.formatShortFileSize(context, entry.totalBytes)

            txtPath.text = entry.relativePath

            coverImageLoader.load(imgCover, entry.coverSource)

            val exportEnabled = entry.status == BiliCacheStatus.AVAILABLE
            txtStatus.visibility = if (exportEnabled) View.VISIBLE else View.GONE
            btnExportAction.setText(R.string.export_mp4)
            btnExportAction.isEnabled = exportEnabled
            btnExportAction.setOnClickListener { onExport(entry) }
            btnM4aExportAction.setText(R.string.export_m4a)
            btnM4aExportAction.isEnabled = exportEnabled
            btnM4aExportAction.setOnClickListener { onM4aExport(entry) }
            btnMp3ExportAction.setText(R.string.export_mp3)
            btnMp3ExportAction.isEnabled = exportEnabled
            btnMp3ExportAction.setOnClickListener { onMp3Export(entry) }
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<BiliCacheListItem>() {
            override fun areItemsTheSame(
                oldItem: BiliCacheListItem,
                newItem: BiliCacheListItem
            ): Boolean = oldItem.entry.id == newItem.entry.id

            override fun areContentsTheSame(
                oldItem: BiliCacheListItem,
                newItem: BiliCacheListItem
            ): Boolean = oldItem == newItem
        }
    }
}
