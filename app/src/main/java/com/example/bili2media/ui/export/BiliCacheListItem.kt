package com.example.bili2media.ui.export

import com.example.bili2media.cache.model.BiliCacheEntry
import com.example.bili2media.ui.export.m4a.M4aExportUiState

data class BiliCacheListItem(
    val entry: BiliCacheEntry,
    val exportState: Mp4ExportUiState = Mp4ExportUiState.Idle,
    val m4aExportState: M4aExportUiState = M4aExportUiState.Idle
)
