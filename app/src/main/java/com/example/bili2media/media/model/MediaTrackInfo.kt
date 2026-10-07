package com.example.bili2media.media.model

data class MediaTrackInfo(
    val index: Int,
    val kind: MediaTrackKind,
    val mimeType: String,
    val durationUs: Long?,
    val width: Int?,
    val height: Int?,
    val bitrate: Int?,
    val sampleRate: Int? = null,
    val channelCount: Int? = null
)
