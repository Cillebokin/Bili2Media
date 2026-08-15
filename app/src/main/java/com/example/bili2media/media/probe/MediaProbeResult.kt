package com.example.bili2media.media.probe

import com.example.bili2media.cache.model.CacheMediaFile
import com.example.bili2media.media.model.ProbedMediaFile

sealed interface MediaProbeResult {
    data class Success(
        val media: ProbedMediaFile
    ) : MediaProbeResult

    data class Failure(
        val file: CacheMediaFile,
        val reason: MediaProbeFailure
    ) : MediaProbeResult
}

enum class MediaProbeFailure {
    INPUT_UNAVAILABLE,
    MALFORMED_MEDIA,
    READ_FAILED
}
