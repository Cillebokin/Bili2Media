package com.example.bili2media.cache.model

sealed interface MediaInputRef {
    data class FilePath(val path: String) : MediaInputRef

    data class ContentUri(val uri: String) : MediaInputRef
}
