package com.example.bili2media.ui.export.m4a

import androidx.work.WorkInfo
import com.example.bili2media.export.m4a.work.M4aExportWorkContract

class M4aExportStateMapper {
    fun map(workInfos: List<WorkInfo>): Map<String, M4aExportUiState> {
        val newestByEntry = linkedMapOf<String, OrderedWorkInfo>()
        workInfos.forEach { workInfo ->
            val entryId = M4aExportWorkContract.entryIdFromTags(workInfo.tags)
                ?: return@forEach
            val candidate = OrderedWorkInfo(
                requestOrder = M4aExportWorkContract.requestOrderFromTags(workInfo.tags),
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
        val candidateOrder = requestOrder
        val currentOrder = other.requestOrder
        return when {
            candidateOrder != null && currentOrder == null -> true
            candidateOrder == null && currentOrder != null -> false
            candidateOrder != null && currentOrder != null && candidateOrder != currentOrder -> {
                candidateOrder > currentOrder
            }
            else -> workInfo.generation > other.workInfo.generation
        }
    }

    private fun mapState(workInfo: WorkInfo): M4aExportUiState {
        return when (workInfo.state) {
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.BLOCKED -> M4aExportUiState.Queued

            WorkInfo.State.RUNNING -> {
                when (workInfo.progress.getString(M4aExportWorkContract.KEY_PHASE)) {
                    M4aExportWorkContract.PHASE_ANALYZING -> M4aExportUiState.Analyzing
                    else -> M4aExportUiState.Exporting(
                        workInfo.progress.getInt(
                            M4aExportWorkContract.KEY_PROGRESS,
                            0
                        ).coerceIn(0, 100)
                    )
                }
            }

            WorkInfo.State.SUCCEEDED -> {
                val outputUri = workInfo.outputData.getString(
                    M4aExportWorkContract.KEY_OUTPUT_URI
                )
                if (outputUri.isNullOrBlank()) {
                    M4aExportUiState.Failed(MISSING_OUTPUT_URI)
                } else {
                    M4aExportUiState.Succeeded(outputUri)
                }
            }

            WorkInfo.State.FAILED -> M4aExportUiState.Failed(
                workInfo.outputData.getString(M4aExportWorkContract.KEY_ERROR_CODE)
                    ?.takeIf { it.isNotBlank() }
                    ?: UNKNOWN_ERROR
            )

            WorkInfo.State.CANCELLED -> M4aExportUiState.Cancelled
        }
    }

    private data class OrderedWorkInfo(
        val requestOrder: Long?,
        val workInfo: WorkInfo
    )

    private companion object {
        const val MISSING_OUTPUT_URI = "MISSING_OUTPUT_URI"
        const val UNKNOWN_ERROR = "UNKNOWN_ERROR"
    }
}
