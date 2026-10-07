package com.example.bili2media.export.m4a.work

import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.workDataOf
import com.example.bili2media.export.m4a.usecase.M4aExportListener
import com.example.bili2media.export.m4a.usecase.M4aExportOutcome
import com.example.bili2media.export.m4a.usecase.M4aExportRequest
import kotlinx.coroutines.CancellationException

internal class M4aExportWorkerCoordinator(
    private val export: (M4aExportRequest, M4aExportListener) -> M4aExportOutcome,
    private val isStopped: () -> Boolean,
    private val publishProgress: (Data) -> Unit
) {
    suspend fun execute(
        inputData: Data,
        enterForeground: suspend () -> Unit
    ): ListenableWorker.Result {
        val request = M4aExportWorkContract.decodeRequest(inputData)
            ?: return failure(INVALID_INPUT)
        enterForeground()

        val listener = object : M4aExportListener {
            override fun onAnalyzing() {
                publishProgress(progressData(request.entryId, null))
            }

            override fun onProgress(percent: Int) {
                publishProgress(progressData(request.entryId, percent.coerceIn(0, 100)))
            }

            override fun isCancelled(): Boolean = isStopped()
        }

        return when (val outcome = export(request, listener)) {
            is M4aExportOutcome.Success -> ListenableWorker.Result.success(
                workDataOf(
                    M4aExportWorkContract.KEY_ENTRY_ID to request.entryId,
                    M4aExportWorkContract.KEY_OUTPUT_URI to outcome.outputUri
                )
            )

            is M4aExportOutcome.Unsupported -> failure(
                "UNSUPPORTED_${outcome.reason.name}",
                request.entryId
            )

            is M4aExportOutcome.Failure -> failure(outcome.code, request.entryId)
            M4aExportOutcome.Cancelled -> throw CancellationException("M4A export cancelled")
        }
    }

    private fun progressData(entryId: String, progress: Int?): Data {
        val phase = if (progress == null) {
            M4aExportWorkContract.PHASE_ANALYZING
        } else {
            M4aExportWorkContract.PHASE_EXPORTING
        }
        return workDataOf(
            M4aExportWorkContract.KEY_ENTRY_ID to entryId,
            M4aExportWorkContract.KEY_PHASE to phase,
            M4aExportWorkContract.KEY_PROGRESS to (progress ?: 0)
        )
    }

    private fun failure(errorCode: String, entryId: String? = null): ListenableWorker.Result {
        val data = Data.Builder()
            .putString(M4aExportWorkContract.KEY_ERROR_CODE, errorCode)
            .apply {
                if (entryId != null) {
                    putString(M4aExportWorkContract.KEY_ENTRY_ID, entryId)
                }
            }
            .build()
        return ListenableWorker.Result.failure(data)
    }

    private companion object {
        const val INVALID_INPUT = "INVALID_INPUT"
    }
}
