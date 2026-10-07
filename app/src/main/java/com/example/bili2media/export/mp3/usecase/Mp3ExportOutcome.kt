package com.example.bili2media.export.mp3.usecase

import com.example.bili2media.export.mp3.model.Mp3UnsupportedReason

sealed interface Mp3ExportOutcome {
    data class Success(val outputUri: String) : Mp3ExportOutcome
    data class Unsupported(val reason: Mp3UnsupportedReason) : Mp3ExportOutcome
    data class Failure(val code: String) : Mp3ExportOutcome
    data object Cancelled : Mp3ExportOutcome
}
