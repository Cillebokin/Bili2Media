package com.example.bili2media.cache.cover

import com.example.bili2media.cache.model.CoverSource
import java.util.Locale

class BiliCacheCoverResolver {
    fun isLocalCoverFileName(fileName: String): Boolean {
        return fileName.lowercase(Locale.ROOT) in LOCAL_COVER_FILE_NAMES
    }

    fun resolve(localCoverUri: String?, remoteCoverUrl: String?): CoverSource? {
        localCoverUri?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return CoverSource.Local(it) }

        return normalizeRemoteUrl(remoteCoverUrl)?.let(CoverSource::Remote)
    }

    private fun normalizeRemoteUrl(remoteCoverUrl: String?): String? {
        val value = remoteCoverUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val normalized = when {
            value.startsWith("//") -> "https:$value"
            value.startsWith(HTTP_PREFIX, ignoreCase = true) -> {
                HTTPS_PREFIX + value.substring(HTTP_PREFIX.length)
            }
            value.startsWith(HTTPS_PREFIX, ignoreCase = true) -> {
                HTTPS_PREFIX + value.substring(HTTPS_PREFIX.length)
            }
            else -> return null
        }
        return normalized.takeIf { it.length > HTTPS_PREFIX.length }
    }

    private companion object {
        const val HTTP_PREFIX = "http://"
        const val HTTPS_PREFIX = "https://"
        val LOCAL_COVER_FILE_NAMES = setOf(
            "cover.jpg",
            "cover.jpeg",
            "cover.png",
            "cover.webp"
        )
    }
}
