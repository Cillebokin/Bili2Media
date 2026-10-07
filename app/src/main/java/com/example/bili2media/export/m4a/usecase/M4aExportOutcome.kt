package com.example.bili2media.export.m4a.usecase

import com.example.bili2media.export.m4a.model.M4aUnsupportedReason

sealed interface M4aExportOutcome {
    data class Success(
        val outputUri: String
    ) : M4aExportOutcome

    data class Unsupported(
        val reason: M4aUnsupportedReason
    ) : M4aExportOutcome

    data class Failure(
        val code: String
    ) : M4aExportOutcome

    data object Cancelled : M4aExportOutcome
}
