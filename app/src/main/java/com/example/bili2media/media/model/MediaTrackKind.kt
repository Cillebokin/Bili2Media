package com.example.bili2media.media.model

import java.util.Locale

enum class MediaTrackKind {
    VIDEO,
    AUDIO,
    OTHER;

    companion object {
        fun fromMime(mimeType: String): MediaTrackKind {
            return when {
                mimeType.trim().lowercase(Locale.ROOT).startsWith("video/") -> VIDEO
                mimeType.trim().lowercase(Locale.ROOT).startsWith("audio/") -> AUDIO
                else -> OTHER
            }
        }
    }
}
