package com.example.bili2media.ui.export.mp3

sealed interface Mp3ExportUiState {
    data object Idle : Mp3ExportUiState
    data object Queued : Mp3ExportUiState
    data object Analyzing : Mp3ExportUiState
    data class Exporting(val progress: Int) : Mp3ExportUiState
    data class Succeeded(val outputUri: String) : Mp3ExportUiState
    data class Failed(val errorCode: String) : Mp3ExportUiState
    data object Cancelled : Mp3ExportUiState
}
