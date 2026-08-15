package com.example.bili2media.ui.export

import com.example.bili2media.cache.model.BiliCacheEntry

data class BiliCacheListItem(
    val entry: BiliCacheEntry,
    val exportState: Mp4ExportUiState = Mp4ExportUiState.Idle
)
