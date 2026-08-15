package com.example.bili2media.cache.model

sealed interface CacheEntryLocation {
    data class FileDirectory(val path: String) : CacheEntryLocation

    data class DocumentDirectory(val uri: String) : CacheEntryLocation
}
