package com.example.bili2media.export.m4a.engine

sealed interface M4aEngineResult {
    data object Success : M4aEngineResult

    data object Cancelled : M4aEngineResult

    data class Failure(
        val error: M4aEngineError
    ) : M4aEngineResult
}
