package com.example.bili2media.export.mp3.model

import com.example.bili2media.export.model.MediaTrackSelection

data class Mp3ExportPlan(
    val selection: MediaTrackSelection
)

sealed interface Mp3ExportPlanningResult {
    data class Ready(val plan: Mp3ExportPlan) : Mp3ExportPlanningResult
    data class Unsupported(val reason: Mp3UnsupportedReason) : Mp3ExportPlanningResult
}
