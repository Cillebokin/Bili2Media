package com.example.bili2media.ui.export.mp3

import androidx.work.WorkInfo
import com.example.bili2media.export.mp3.work.Mp3ExportWorkContract

class Mp3ExportStateMapper {
    fun map(workInfos: List<WorkInfo>): Map<String, Mp3ExportUiState> {
        val newestByEntry = linkedMapOf<String, OrderedWorkInfo>()
        workInfos.forEach { workInfo ->
            val entryId = Mp3ExportWorkContract.entryIdFromTags(workInfo.tags) ?: return@forEach
            val candidate = OrderedWorkInfo(
                Mp3ExportWorkContract.requestOrderFromTags(workInfo.tags),
                workInfo
            )
            val current = newestByEntry[entryId]
            if (current == null || candidate.isNewerThan(current)) newestByEntry[entryId] = candidate
        }
        return newestByEntry.mapValues { (_, ordered) -> mapState(ordered.workInfo) }
    }

    private fun OrderedWorkInfo.isNewerThan(other: OrderedWorkInfo): Boolean = when {
        requestOrder != null && other.requestOrder == null -> true
        requestOrder == null && other.requestOrder != null -> false
        requestOrder != null && other.requestOrder != null && requestOrder != other.requestOrder ->
            requestOrder > other.requestOrder
        else -> workInfo.generation > other.workInfo.generation
    }

    private fun mapState(workInfo: WorkInfo): Mp3ExportUiState = when (workInfo.state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> Mp3ExportUiState.Queued
        WorkInfo.State.RUNNING -> when (
            workInfo.progress.getString(Mp3ExportWorkContract.KEY_PHASE)
        ) {
            Mp3ExportWorkContract.PHASE_ANALYZING -> Mp3ExportUiState.Analyzing
            else -> Mp3ExportUiState.Exporting(
                workInfo.progress.getInt(Mp3ExportWorkContract.KEY_PROGRESS, 0).coerceIn(0, 100)
            )
        }

        WorkInfo.State.SUCCEEDED -> workInfo.outputData
            .getString(Mp3ExportWorkContract.KEY_OUTPUT_URI)
            ?.takeIf(String::isNotBlank)
            ?.let(Mp3ExportUiState::Succeeded)
            ?: Mp3ExportUiState.Failed(MISSING_OUTPUT_URI)

        WorkInfo.State.FAILED -> Mp3ExportUiState.Failed(
            workInfo.outputData.getString(Mp3ExportWorkContract.KEY_ERROR_CODE)
                ?.takeIf(String::isNotBlank)
                ?: UNKNOWN_ERROR
        )

        WorkInfo.State.CANCELLED -> Mp3ExportUiState.Cancelled
    }

    private data class OrderedWorkInfo(val requestOrder: Long?, val workInfo: WorkInfo)

    private companion object {
        const val MISSING_OUTPUT_URI = "MISSING_OUTPUT_URI"
        const val UNKNOWN_ERROR = "UNKNOWN_ERROR"
    }
}
