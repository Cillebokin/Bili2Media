package com.example.bili2media.ui

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.bili2media.R
import com.example.bili2media.cache.model.BiliCacheEntry
import com.example.bili2media.cache.model.BiliCacheStatus
import com.example.bili2media.ui.export.BiliCacheListItem
import com.example.bili2media.ui.export.Mp4ExportUiState
import com.example.bili2media.ui.image.CoverImageLoader

class BiliCacheAdapter(
    private val coverImageLoader: CoverImageLoader,
    private val onExport: (BiliCacheEntry) -> Unit,
    private val onCancel: (String) -> Unit,
    private val onOpenOutput: (String) -> Unit
) : ListAdapter<BiliCacheListItem, BiliCacheAdapter.CacheViewHolder>(DIFF_CALLBACK) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CacheViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bili_cache, parent, false)
        return CacheViewHolder(
            itemView = view,
            coverImageLoader = coverImageLoader,
            onExport = onExport,
            onCancel = onCancel,
            onOpenOutput = onOpenOutput
        )
    }

    override fun onBindViewHolder(holder: CacheViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CacheViewHolder(
        itemView: View,
        private val coverImageLoader: CoverImageLoader,
        private val onExport: (BiliCacheEntry) -> Unit,
        private val onCancel: (String) -> Unit,
        private val onOpenOutput: (String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val imgCover: ImageView = itemView.findViewById(R.id.imgCacheCover)
        private val txtTitle: TextView = itemView.findViewById(R.id.txtCacheTitle)
        private val txtSubtitle: TextView = itemView.findViewById(R.id.txtCacheSubtitle)
        private val txtStatus: TextView = itemView.findViewById(R.id.txtCacheStatus)
        private val txtDetails: TextView = itemView.findViewById(R.id.txtCacheDetails)
        private val txtIds: TextView = itemView.findViewById(R.id.txtCacheIds)
        private val txtPath: TextView = itemView.findViewById(R.id.txtCachePath)
        private val progressExport: ProgressBar = itemView.findViewById(R.id.progressExport)
        private val txtExportStatus: TextView = itemView.findViewById(R.id.txtExportStatus)
        private val btnExportAction: Button = itemView.findViewById(R.id.btnExportAction)

        fun bind(item: BiliCacheListItem) {
            val entry = item.entry
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
            bindExportState(entry, item.exportState)
        }

        private fun bindExportState(entry: BiliCacheEntry, state: Mp4ExportUiState) {
            val context = itemView.context
            progressExport.visibility = View.GONE
            progressExport.isIndeterminate = true
            txtExportStatus.setTextColor(
                ContextCompat.getColor(context, R.color.bili2media_text_secondary)
            )
            btnExportAction.setOnClickListener(null)

            when (state) {
                Mp4ExportUiState.Idle -> {
                    txtExportStatus.setText(R.string.export_status_ready)
                    btnExportAction.setText(R.string.export_mp4)
                    btnExportAction.isEnabled = entry.status == BiliCacheStatus.AVAILABLE
                    btnExportAction.setOnClickListener { onExport(entry) }
                }

                Mp4ExportUiState.Queued -> {
                    txtExportStatus.setText(R.string.export_status_queued)
                    bindCancel(entry)
                }

                Mp4ExportUiState.Analyzing -> {
                    progressExport.visibility = View.VISIBLE
                    txtExportStatus.setText(R.string.export_status_analyzing)
                    bindCancel(entry)
                }

                is Mp4ExportUiState.Exporting -> {
                    progressExport.visibility = View.VISIBLE
                    progressExport.isIndeterminate = false
                    progressExport.progress = state.progress
                    txtExportStatus.text = context.getString(
                        R.string.export_status_progress,
                        state.progress
                    )
                    bindCancel(entry)
                }

                is Mp4ExportUiState.Succeeded -> {
                    txtExportStatus.setText(R.string.export_status_succeeded)
                    btnExportAction.setText(R.string.open_mp4)
                    btnExportAction.isEnabled = true
                    btnExportAction.setOnClickListener {
                        onOpenOutput(state.outputUri)
                    }
                }

                is Mp4ExportUiState.Failed -> {
                    txtExportStatus.setText(R.string.export_status_failed)
                    txtExportStatus.setTextColor(
                        ContextCompat.getColor(context, R.color.bili2media_danger)
                    )
                    bindRetry(entry)
                }

                Mp4ExportUiState.Cancelled -> {
                    txtExportStatus.setText(R.string.export_status_cancelled)
                    bindRetry(entry)
                }
            }
        }

        private fun bindCancel(entry: BiliCacheEntry) {
            btnExportAction.setText(R.string.cancel_export)
            btnExportAction.isEnabled = true
            btnExportAction.setOnClickListener { onCancel(entry.id) }
        }

        private fun bindRetry(entry: BiliCacheEntry) {
            btnExportAction.setText(R.string.retry_export)
            btnExportAction.isEnabled = entry.status == BiliCacheStatus.AVAILABLE
            btnExportAction.setOnClickListener { onExport(entry) }
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<BiliCacheListItem>() {
            override fun areItemsTheSame(
                oldItem: BiliCacheListItem,
                newItem: BiliCacheListItem
            ): Boolean {
                return oldItem.entry.id == newItem.entry.id
            }

            override fun areContentsTheSame(
                oldItem: BiliCacheListItem,
                newItem: BiliCacheListItem
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}
