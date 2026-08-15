package com.example.bili2media.media.probe

import com.example.bili2media.cache.model.CacheMediaFile

fun interface MediaProbe {
    fun probe(file: CacheMediaFile): MediaProbeResult
}
