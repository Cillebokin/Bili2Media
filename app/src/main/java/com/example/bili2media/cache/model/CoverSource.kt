package com.example.bili2media.cache.model

sealed interface CoverSource {
    data class Local(val uri: String) : CoverSource

    data class Remote(val url: String) : CoverSource
}
