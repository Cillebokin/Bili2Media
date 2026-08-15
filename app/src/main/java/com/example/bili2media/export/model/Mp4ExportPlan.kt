package com.example.bili2media.export.model

import com.example.bili2media.cache.model.MediaInputRef

sealed interface Mp4ExportPlan {
    data class CopyExistingMp4(
        val input: MediaInputRef,
        val sourceBytes: Long,
        val durationUs: Long
    ) : Mp4ExportPlan

    data class MuxM4s(
        val video: MediaTrackSelection,
        val audio: MediaTrackSelection,
        val durationUs: Long
    ) : Mp4ExportPlan
}

sealed interface Mp4ExportPlanningResult {
    data class Ready(
        val plan: Mp4ExportPlan
    ) : Mp4ExportPlanningResult

    data class Unsupported(
        val reason: Mp4UnsupportedReason
    ) : Mp4ExportPlanningResult
}
