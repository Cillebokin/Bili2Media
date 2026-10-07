package com.example.bili2media.export.m4a.usecase

import com.example.bili2media.cache.model.CacheEntryLocation

data class M4aExportRequest(
    val entryId: String,
    val title: String,
    val location: CacheEntryLocation
)
