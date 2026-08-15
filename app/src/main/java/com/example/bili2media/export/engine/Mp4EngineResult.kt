package com.example.bili2media.export.engine

sealed interface Mp4EngineResult {
    data object Success : Mp4EngineResult

    data object Cancelled : Mp4EngineResult

    data class Failure(
        val error: Mp4EngineError
    ) : Mp4EngineResult
}
