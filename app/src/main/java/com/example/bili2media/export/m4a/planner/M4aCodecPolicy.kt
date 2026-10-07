package com.example.bili2media.export.m4a.planner

class M4aCodecPolicy {
    fun isSupportedAudio(mimeType: String): Boolean {
        return mimeType.equals(SUPPORTED_AUDIO_MIME_TYPE, ignoreCase = true)
    }

    companion object {
        const val SUPPORTED_AUDIO_MIME_TYPE = "audio/mp4a-latm"
    }
}
