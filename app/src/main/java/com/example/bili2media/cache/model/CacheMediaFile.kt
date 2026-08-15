package com.example.bili2media.cache.model

data class CacheMediaFile(
    val name: String,
    val relativePath: String,
    val size: Long,
    val input: MediaInputRef
)
