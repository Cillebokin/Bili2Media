package com.example.bili2media.ui.export

sealed interface Mp4ExportUiState {
    data object Idle : Mp4ExportUiState

    data object Queued : Mp4ExportUiState

    data object Analyzing : Mp4ExportUiState

    data class Exporting(
        val progress: Int
    ) : Mp4ExportUiState

    data class Succeeded(
        val outputUri: String
    ) : Mp4ExportUiState

    data class Failed(
        val errorCode: String
    ) : Mp4ExportUiState

    data object Cancelled : Mp4ExportUiState
}
