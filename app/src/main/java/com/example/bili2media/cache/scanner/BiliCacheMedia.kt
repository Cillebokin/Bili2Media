package com.example.bili2media.cache.scanner

import java.util.Locale

data class BiliCacheMedia(
    val name: String,
    val size: Long
) {
    companion object {
        private val supportedExtensions = setOf(
            "m4s",
            "blv",
            "flv",
            "mp4",
            "m4a",
            "aac"
        )

        fun isSupportedFileName(fileName: String): Boolean {
            val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
                .lowercase(Locale.ROOT)
            return extension in supportedExtensions
        }
    }
}
