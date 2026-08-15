package com.example.bili2media.cache.model

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal object CacheEntryIdentity {
    fun stableId(location: CacheEntryLocation): String {
        val identity = when (location) {
            is CacheEntryLocation.FileDirectory -> "file\u0000${location.path}"
            is CacheEntryLocation.DocumentDirectory -> "document\u0000${location.uri}"
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(StandardCharsets.UTF_8))
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(HEX_DIGITS[value ushr 4])
                append(HEX_DIGITS[value and 0x0f])
            }
        }
    }

    private const val HEX_DIGITS = "0123456789abcdef"
}
