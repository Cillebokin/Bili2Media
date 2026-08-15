package com.example.bili2media.ui.export

import androidx.work.WorkInfo
import com.example.bili2media.export.work.Mp4ExportWorkContract

class Mp4ExportStateMapper {
    fun map(workInfos: List<WorkInfo>): Map<String, Mp4ExportUiState> {
        val newestByEntry = linkedMapOf<String, OrderedWorkInfo>()
        workInfos.forEach { workInfo ->
            val entryId = Mp4ExportWorkContract.entryIdFromTags(workInfo.tags)
                ?: return@forEach
            val candidate = OrderedWorkInfo(
                requestOrder = Mp4ExportWorkContract.requestOrderFromTags(workInfo.tags)
                    ?: LEGACY_REQUEST_ORDER,
                workInfo = workInfo
            )
            val current = newestByEntry[entryId]
            if (current == null || candidate.isNewerThan(current)) {
                newestByEntry[entryId] = candidate
            }
        }
        return newestByEntry.mapValues { (_, ordered) -> mapState(ordered.workInfo) }
    }

    private fun OrderedWorkInfo.isNewerThan(other: OrderedWorkInfo): Boolean {
        return requestOrder > other.requestOrder ||
            requestOrder == other.requestOrder &&
            workInfo.generation > other.workInfo.generation
    }

    private fun mapState(workInfo: WorkInfo): Mp4ExportUiState {
        return when (workInfo.state) {
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.BLOCKED -> Mp4ExportUiState.Queued

            WorkInfo.State.RUNNING -> {
                when (workInfo.progress.getString(Mp4ExportWorkContract.KEY_PHASE)) {
                    Mp4ExportWorkContract.PHASE_ANALYZING -> Mp4ExportUiState.Analyzing
                    else -> Mp4ExportUiState.Exporting(
                        workInfo.progress.getInt(
                            Mp4ExportWorkContract.KEY_PROGRESS,
                            0
                        ).coerceIn(0, 100)
                    )
                }
            }

            WorkInfo.State.SUCCEEDED -> {
                val outputUri = workInfo.outputData.getString(
                    Mp4ExportWorkContract.KEY_OUTPUT_URI
                )
                if (outputUri.isNullOrBlank()) {
                    Mp4ExportUiState.Failed(MISSING_OUTPUT_URI)
                } else {
                    Mp4ExportUiState.Succeeded(outputUri)
                }
            }

            WorkInfo.State.FAILED -> Mp4ExportUiState.Failed(
                workInfo.outputData.getString(Mp4ExportWorkContract.KEY_ERROR_CODE)
                    ?.takeIf { it.isNotBlank() }
                    ?: UNKNOWN_ERROR
            )

            WorkInfo.State.CANCELLED -> Mp4ExportUiState.Cancelled
        }
    }

    private companion object {
        const val LEGACY_REQUEST_ORDER = Long.MIN_VALUE
        const val MISSING_OUTPUT_URI = "MISSING_OUTPUT_URI"
        const val UNKNOWN_ERROR = "UNKNOWN_ERROR"
    }

    private data class OrderedWorkInfo(
        val requestOrder: Long,
        val workInfo: WorkInfo
    )
}
