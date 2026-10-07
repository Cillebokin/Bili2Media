package com.example.bili2media.export.mp3.engine

enum class Mp3EngineError {
    INPUT_OPEN_FAILED,
    OUTPUT_OPEN_FAILED,
    INVALID_TRACK,
    UNSUPPORTED_AUDIO_FORMAT,
    DECODER_FAILED,
    ENCODER_FAILED,
    OUTPUT_WRITE_FAILED
}
