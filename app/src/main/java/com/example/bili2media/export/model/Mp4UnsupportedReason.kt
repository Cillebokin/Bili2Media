package com.example.bili2media.export.model

enum class Mp4UnsupportedReason {
    NO_MEDIA,
    PROBE_FAILED,
    MISSING_VIDEO,
    MISSING_AUDIO,
    AMBIGUOUS_TRACKS,
    MULTI_SEGMENT,
    UNSUPPORTED_CODEC
}
