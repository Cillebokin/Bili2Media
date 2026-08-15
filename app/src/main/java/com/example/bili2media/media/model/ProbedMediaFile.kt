package com.example.bili2media.media.model

import com.example.bili2media.cache.model.CacheMediaFile

data class ProbedMediaFile(
    val file: CacheMediaFile,
    val tracks: List<MediaTrackInfo>
)
