package com.example.bili2media.export.planner

import java.util.Locale

class Mp4CodecPolicy {
    fun isSupportedVideo(mimeType: String): Boolean {
        return mimeType.normalized() in SUPPORTED_VIDEO_MIME_TYPES
    }

    fun isSupportedAudio(mimeType: String): Boolean {
        return mimeType.normalized() == SUPPORTED_AUDIO_MIME_TYPE
    }

    private fun String.normalized(): String {
        return trim().lowercase(Locale.ROOT)
    }

    private companion object {
        val SUPPORTED_VIDEO_MIME_TYPES = setOf("video/avc", "video/hevc")
        const val SUPPORTED_AUDIO_MIME_TYPE = "audio/mp4a-latm"
    }
}
