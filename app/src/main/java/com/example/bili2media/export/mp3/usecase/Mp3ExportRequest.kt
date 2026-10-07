package com.example.bili2media.export.mp3.usecase

import com.example.bili2media.cache.model.CacheEntryLocation

data class Mp3ExportRequest(
    val entryId: String,
    val title: String,
    val location: CacheEntryLocation
)
