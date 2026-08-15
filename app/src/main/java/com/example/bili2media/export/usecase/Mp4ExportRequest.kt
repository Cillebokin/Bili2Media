package com.example.bili2media.export.usecase

import com.example.bili2media.cache.model.CacheEntryLocation

data class Mp4ExportRequest(
    val entryId: String,
    val title: String,
    val location: CacheEntryLocation
)
