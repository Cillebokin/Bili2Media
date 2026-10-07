package com.example.bili2media.export.m4a.model

import com.example.bili2media.cache.model.MediaInputRef
import com.example.bili2media.export.model.MediaTrackSelection

sealed interface M4aExportPlan {
    data class CopyExistingM4a(
        val input: MediaInputRef,
        val sourceBytes: Long,
        val durationUs: Long
    ) : M4aExportPlan

    data class RemuxAacTrack(
        val selection: MediaTrackSelection
    ) : M4aExportPlan
}

sealed interface M4aExportPlanningResult {
    data class Ready(val plan: M4aExportPlan) : M4aExportPlanningResult
    data class Unsupported(val reason: M4aUnsupportedReason) : M4aExportPlanningResult
}
