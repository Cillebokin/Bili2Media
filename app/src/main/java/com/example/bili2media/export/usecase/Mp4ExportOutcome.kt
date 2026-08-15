package com.example.bili2media.export.usecase

import com.example.bili2media.export.model.Mp4UnsupportedReason

sealed interface Mp4ExportOutcome {
    data class Success(
        val outputUri: String
    ) : Mp4ExportOutcome

    data class Unsupported(
        val reason: Mp4UnsupportedReason
    ) : Mp4ExportOutcome

    data class Failure(
        val code: String
    ) : Mp4ExportOutcome

    data object Cancelled : Mp4ExportOutcome
}
