package com.example.bili2media.export.mp3.engine

sealed interface Mp3EngineResult {
    data object Success : Mp3EngineResult
    data object Cancelled : Mp3EngineResult
    data class Failure(val error: Mp3EngineError) : Mp3EngineResult
}
