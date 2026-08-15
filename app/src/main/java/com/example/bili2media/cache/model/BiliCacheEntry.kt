package com.example.bili2media.cache.model

data class BiliCacheEntry(
    val id: String,
    val title: String,
    val subtitle: String?,
    val avid: Long?,
    val cid: Long?,
    val relativePath: String,
    val location: CacheEntryLocation,
    val mediaFileCount: Int,
    val totalBytes: Long,
    val status: BiliCacheStatus,
    val coverSource: CoverSource? = null
)

enum class BiliCacheStatus {
    AVAILABLE,
    NO_MEDIA,
    METADATA_ERROR
}
