package com.example.bili2media.ui.export.m4a

sealed interface M4aExportUiState {
    data object Idle : M4aExportUiState

    data object Queued : M4aExportUiState

    data object Analyzing : M4aExportUiState

    data class Exporting(
        val progress: Int
    ) : M4aExportUiState

    data class Succeeded(
        val outputUri: String
    ) : M4aExportUiState

    data class Failed(
        val errorCode: String
    ) : M4aExportUiState

    data object Cancelled : M4aExportUiState
}
