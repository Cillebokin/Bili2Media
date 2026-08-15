package com.example.bili2media.ui.export

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.WorkInfo
import com.example.bili2media.export.work.Mp4ExportWorkContract
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class Mp4ExportStateMapperTest {
    private val mapper = Mp4ExportStateMapper()

    @Test
    fun map_mapsQueuedRunningAndTerminalStates() {
        val workInfos = listOf(
            workInfo("queued", WorkInfo.State.ENQUEUED),
            workInfo(
                "analyzing",
                WorkInfo.State.RUNNING,
                progress = Data.Builder()
                    .putString(
                        Mp4ExportWorkContract.KEY_PHASE,
                        Mp4ExportWorkContract.PHASE_ANALYZING
                    )
                    .build()
            ),
            workInfo(
                "exporting",
                WorkInfo.State.RUNNING,
                progress = Data.Builder()
                    .putString(
                        Mp4ExportWorkContract.KEY_PHASE,
                        Mp4ExportWorkContract.PHASE_EXPORTING
                    )
                    .putInt(Mp4ExportWorkContract.KEY_PROGRESS, 37)
                    .build()
            ),
            workInfo(
                "succeeded",
                WorkInfo.State.SUCCEEDED,
                output = Data.Builder()
                    .putString(Mp4ExportWorkContract.KEY_OUTPUT_URI, "content://output/1")
                    .build()
            ),
            workInfo(
                "failed",
                WorkInfo.State.FAILED,
                output = Data.Builder()
                    .putString(Mp4ExportWorkContract.KEY_ERROR_CODE, "ENGINE_MUXER_FAILED")
                    .build()
            ),
            workInfo("cancelled", WorkInfo.State.CANCELLED)
        )

        val states = mapper.map(workInfos)

        assertEquals(Mp4ExportUiState.Queued, states["queued"])
        assertEquals(Mp4ExportUiState.Analyzing, states["analyzing"])
        assertEquals(Mp4ExportUiState.Exporting(37), states["exporting"])
        assertEquals(
            Mp4ExportUiState.Succeeded("content://output/1"),
            states["succeeded"]
        )
        assertEquals(
            Mp4ExportUiState.Failed("ENGINE_MUXER_FAILED"),
            states["failed"]
        )
        assertEquals(Mp4ExportUiState.Cancelled, states["cancelled"])
    }

    @Test
    fun map_usesNewestGenerationForDuplicateEntryWork() {
        val old = workInfo(
            entryId = "entry",
            state = WorkInfo.State.FAILED,
            generation = 1,
            output = Data.Builder()
                .putString(Mp4ExportWorkContract.KEY_ERROR_CODE, "OLD")
                .build()
        )
        val newest = workInfo(
            entryId = "entry",
            state = WorkInfo.State.RUNNING,
            generation = 2,
            progress = Data.Builder()
                .putString(
                    Mp4ExportWorkContract.KEY_PHASE,
                    Mp4ExportWorkContract.PHASE_EXPORTING
                )
                .putInt(Mp4ExportWorkContract.KEY_PROGRESS, 75)
                .build()
        )

        assertEquals(
            Mp4ExportUiState.Exporting(75),
            mapper.map(listOf(newest, old))["entry"]
        )
    }

    @Test
    fun map_usesNewestRequestOrderWhenRetryCreatesANewWorkRequest() {
        val old = workInfo(
            entryId = "entry",
            state = WorkInfo.State.SUCCEEDED,
            requestOrder = 100L,
            output = Data.Builder()
                .putString(Mp4ExportWorkContract.KEY_OUTPUT_URI, "content://output/old")
                .build()
        )
        val newest = workInfo(
            entryId = "entry",
            state = WorkInfo.State.FAILED,
            requestOrder = 200L,
            output = Data.Builder()
                .putString(Mp4ExportWorkContract.KEY_ERROR_CODE, "NEW_FAILURE")
                .build()
        )

        assertEquals(
            Mp4ExportUiState.Failed("NEW_FAILURE"),
            mapper.map(listOf(newest, old))["entry"]
        )
    }

    @Test
    fun map_usesStableFallbacksForMissingTerminalData() {
        val succeeded = workInfo("succeeded", WorkInfo.State.SUCCEEDED)
        val failed = workInfo("failed", WorkInfo.State.FAILED)

        val states = mapper.map(listOf(succeeded, failed))

        assertEquals(
            Mp4ExportUiState.Failed("MISSING_OUTPUT_URI"),
            states["succeeded"]
        )
        assertEquals(
            Mp4ExportUiState.Failed("UNKNOWN_ERROR"),
            states["failed"]
        )
    }

    private fun workInfo(
        entryId: String,
        state: WorkInfo.State,
        generation: Int = 0,
        requestOrder: Long? = null,
        output: Data = Data.EMPTY,
        progress: Data = Data.EMPTY
    ): WorkInfo {
        val tags = mutableSetOf(Mp4ExportWorkContract.entryTag(entryId))
        requestOrder?.let {
            tags += "bili2media:mp4-export-order:$it"
        }
        return WorkInfo(
            id = UUID.nameUUIDFromBytes("$entryId-$generation-$state".toByteArray()),
            state = state,
            tags = tags,
            outputData = output,
            progress = progress,
            runAttemptCount = 0,
            generation = generation,
            constraints = Constraints.Builder().build(),
            initialDelayMillis = 0L,
            periodicityInfo = null,
            nextScheduleTimeMillis = Long.MAX_VALUE,
            stopReason = 0
        )
    }
}
