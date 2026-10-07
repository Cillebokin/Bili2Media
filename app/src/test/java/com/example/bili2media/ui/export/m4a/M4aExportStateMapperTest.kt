package com.example.bili2media.ui.export.m4a

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.WorkInfo
import com.example.bili2media.export.m4a.work.M4aExportWorkContract
import com.example.bili2media.export.work.Mp4ExportWorkContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.UUID

class M4aExportStateMapperTest {
    private val mapper = M4aExportStateMapper()

    @Test
    fun map_mapsEnqueuedAndBlockedToQueued() {
        val states = mapper.map(
            listOf(
                workInfo("enqueued", WorkInfo.State.ENQUEUED),
                workInfo("blocked", WorkInfo.State.BLOCKED)
            )
        )

        assertEquals(M4aExportUiState.Queued, states["enqueued"])
        assertEquals(M4aExportUiState.Queued, states["blocked"])
    }

    @Test
    fun map_mapsAnalyzingPhase() {
        val state = mapper.map(
            listOf(
                workInfo(
                    entryId = "entry",
                    state = WorkInfo.State.RUNNING,
                    progress = Data.Builder()
                        .putString(
                            M4aExportWorkContract.KEY_PHASE,
                            M4aExportWorkContract.PHASE_ANALYZING
                        )
                        .build()
                )
            )
        )["entry"]

        assertEquals(M4aExportUiState.Analyzing, state)
    }

    @Test
    fun map_mapsExportingProgressAndClampsBounds() {
        val states = mapper.map(
            listOf(
                exportingWorkInfo("normal", 37),
                exportingWorkInfo("below", -1),
                exportingWorkInfo("above", 101)
            )
        )

        assertEquals(M4aExportUiState.Exporting(37), states["normal"])
        assertEquals(M4aExportUiState.Exporting(0), states["below"])
        assertEquals(M4aExportUiState.Exporting(100), states["above"])
    }

    @Test
    fun map_mapsSucceededFailedAndCancelled() {
        val states = mapper.map(
            listOf(
                workInfo(
                    entryId = "succeeded",
                    state = WorkInfo.State.SUCCEEDED,
                    output = Data.Builder()
                        .putString(
                            M4aExportWorkContract.KEY_OUTPUT_URI,
                            "content://output/audio"
                        )
                        .build()
                ),
                workInfo(
                    entryId = "failed",
                    state = WorkInfo.State.FAILED,
                    output = Data.Builder()
                        .putString(M4aExportWorkContract.KEY_ERROR_CODE, "MUX_FAILED")
                        .build()
                ),
                workInfo("cancelled", WorkInfo.State.CANCELLED)
            )
        )

        assertEquals(
            M4aExportUiState.Succeeded("content://output/audio"),
            states["succeeded"]
        )
        assertEquals(M4aExportUiState.Failed("MUX_FAILED"), states["failed"])
        assertEquals(M4aExportUiState.Cancelled, states["cancelled"])
    }

    @Test
    fun map_usesStableFallbacksForMissingTerminalData() {
        val states = mapper.map(
            listOf(
                workInfo("missing-uri", WorkInfo.State.SUCCEEDED),
                workInfo(
                    entryId = "blank-uri",
                    state = WorkInfo.State.SUCCEEDED,
                    output = Data.Builder()
                        .putString(M4aExportWorkContract.KEY_OUTPUT_URI, "  ")
                        .build()
                ),
                workInfo("missing-error", WorkInfo.State.FAILED),
                workInfo(
                    entryId = "blank-error",
                    state = WorkInfo.State.FAILED,
                    output = Data.Builder()
                        .putString(M4aExportWorkContract.KEY_ERROR_CODE, "  ")
                        .build()
                )
            )
        )

        assertEquals(
            M4aExportUiState.Failed("MISSING_OUTPUT_URI"),
            states["missing-uri"]
        )
        assertEquals(
            M4aExportUiState.Failed("MISSING_OUTPUT_URI"),
            states["blank-uri"]
        )
        assertEquals(
            M4aExportUiState.Failed("UNKNOWN_ERROR"),
            states["missing-error"]
        )
        assertEquals(
            M4aExportUiState.Failed("UNKNOWN_ERROR"),
            states["blank-error"]
        )
    }

    @Test
    fun map_newerRequestOrderWinsEvenWhenGenerationIsLower() {
        val olderOrder = failedWorkInfo(
            entryId = "entry",
            errorCode = "OLDER_ORDER",
            generation = 20,
            requestOrder = 100L
        )
        val newerOrder = failedWorkInfo(
            entryId = "entry",
            errorCode = "NEWER_ORDER",
            generation = 1,
            requestOrder = 200L
        )

        assertEquals(
            M4aExportUiState.Failed("NEWER_ORDER"),
            mapper.map(listOf(olderOrder, newerOrder))["entry"]
        )
    }

    @Test
    fun map_equalRequestOrderUsesGenerationAsTieBreaker() {
        val olderGeneration = failedWorkInfo(
            entryId = "entry",
            errorCode = "OLDER_GENERATION",
            generation = 1,
            requestOrder = 200L
        )
        val newerGeneration = failedWorkInfo(
            entryId = "entry",
            errorCode = "NEWER_GENERATION",
            generation = 2,
            requestOrder = 200L
        )

        assertEquals(
            M4aExportUiState.Failed("NEWER_GENERATION"),
            mapper.map(listOf(newerGeneration, olderGeneration))["entry"]
        )
    }

    @Test
    fun map_legacyRecordsUseGeneration() {
        val olderGeneration = failedWorkInfo(
            entryId = "entry",
            errorCode = "OLDER_GENERATION",
            generation = 1
        )
        val newerGeneration = failedWorkInfo(
            entryId = "entry",
            errorCode = "NEWER_GENERATION",
            generation = 2
        )

        assertEquals(
            M4aExportUiState.Failed("NEWER_GENERATION"),
            mapper.map(listOf(olderGeneration, newerGeneration))["entry"]
        )
    }

    @Test
    fun map_anyValidOrderedRetryBeatsLegacyRecord() {
        val legacy = failedWorkInfo(
            entryId = "entry",
            errorCode = "LEGACY",
            generation = 99
        )
        val ordered = failedWorkInfo(
            entryId = "entry",
            errorCode = "ORDERED",
            generation = 0,
            requestOrder = Long.MIN_VALUE
        )

        assertEquals(
            M4aExportUiState.Failed("ORDERED"),
            mapper.map(listOf(legacy, ordered))["entry"]
        )
    }

    @Test
    fun map_ignoresMp4OnlyAndMalformedEntryTags() {
        val mp4Only = workInfo(
            entryId = "mp4",
            state = WorkInfo.State.ENQUEUED,
            tags = setOf(Mp4ExportWorkContract.entryTag("mp4"))
        )
        val malformed = workInfo(
            entryId = "malformed",
            state = WorkInfo.State.ENQUEUED,
            tags = setOf("bili2media:m4a-export-entry:")
        )

        val states = mapper.map(listOf(mp4Only, malformed))

        assertFalse(states.containsKey("mp4"))
        assertFalse(states.containsKey("malformed"))
        assertEquals(emptyMap<String, M4aExportUiState>(), states)
    }

    private fun exportingWorkInfo(entryId: String, progressValue: Int): WorkInfo {
        return workInfo(
            entryId = entryId,
            state = WorkInfo.State.RUNNING,
            progress = Data.Builder()
                .putString(
                    M4aExportWorkContract.KEY_PHASE,
                    M4aExportWorkContract.PHASE_EXPORTING
                )
                .putInt(M4aExportWorkContract.KEY_PROGRESS, progressValue)
                .build()
        )
    }

    private fun failedWorkInfo(
        entryId: String,
        errorCode: String,
        generation: Int,
        requestOrder: Long? = null
    ): WorkInfo {
        return workInfo(
            entryId = entryId,
            state = WorkInfo.State.FAILED,
            generation = generation,
            requestOrder = requestOrder,
            output = Data.Builder()
                .putString(M4aExportWorkContract.KEY_ERROR_CODE, errorCode)
                .build()
        )
    }

    private fun workInfo(
        entryId: String,
        state: WorkInfo.State,
        generation: Int = 0,
        requestOrder: Long? = null,
        output: Data = Data.EMPTY,
        progress: Data = Data.EMPTY,
        tags: Set<String>? = null
    ): WorkInfo {
        val resolvedTags = tags?.toMutableSet()
            ?: mutableSetOf(M4aExportWorkContract.entryTag(entryId))
        requestOrder?.let {
            resolvedTags += "bili2media:m4a-export-order:$it"
        }
        return WorkInfo(
            id = UUID.nameUUIDFromBytes(
                "$entryId-$generation-$state-${requestOrder ?: "legacy"}".toByteArray()
            ),
            state = state,
            tags = resolvedTags,
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
