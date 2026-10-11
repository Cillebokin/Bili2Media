package com.example.bili2media.export.planner

import android.os.Build
import java.util.Locale

class Mp4CodecPolicy(
    private val supportsAv1Mp4Muxing: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
) {
    fun isSupportedVideo(mimeType: String): Boolean {
        val normalizedMimeType = mimeType.normalized()
        return normalizedMimeType in SUPPORTED_VIDEO_MIME_TYPES ||
            (normalizedMimeType == AV1_VIDEO_MIME_TYPE && supportsAv1Mp4Muxing)
    }

    fun isSupportedAudio(mimeType: String): Boolean {
        return mimeType.normalized() == SUPPORTED_AUDIO_MIME_TYPE
    }

    private fun String.normalized(): String {
        return trim().lowercase(Locale.ROOT)
    }

    private companion object {
        val SUPPORTED_VIDEO_MIME_TYPES = setOf("video/avc", "video/hevc")
        const val AV1_VIDEO_MIME_TYPE = "video/av01"
        const val SUPPORTED_AUDIO_MIME_TYPE = "audio/mp4a-latm"
    }
}
