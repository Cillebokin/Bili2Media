package com.example.bili2media.export.model

import com.example.bili2media.cache.model.MediaInputRef

data class MediaTrackSelection(
    val input: MediaInputRef,
    val trackIndex: Int,
    val durationUs: Long
)
