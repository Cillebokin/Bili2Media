package com.example.bili2media.export.m4a.work

import android.content.pm.ServiceInfo
import androidx.work.Data
import androidx.work.ListenableWorker
import com.example.bili2media.cache.model.CacheEntryLocation
import com.example.bili2media.export.m4a.model.M4aUnsupportedReason
import com.example.bili2media.export.m4a.usecase.M4aExportOutcome
import com.example.bili2media.export.m4a.usecase.M4aExportRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class M4aExportWorkerCoordinatorTest {
    @Test
    fun invalidInput_returnsInvalidInputFailureWithoutEnteringForeground() = runBlocking {
        var enteredForeground = false
        val result = M4aExportWorkerCoordinator(
            export = { _, _ -> error("Invalid input must not start export") },
            isStopped = { false },
            publishProgress = { error("Invalid input must not publish progress") }
        ).execute(Data.EMPTY) {
            enteredForeground = true
        }

        assertFailure(result, "INVALID_INPUT", null)
        assertFalse(enteredForeground)
    }

    @Test
    fun success_entersForegroundBeforeExportAndReturnsEntryAndOutputUri() = runBlocking {
        val events = mutableListOf<String>()
        val request = request()
        val result = M4aExportWorkerCoordinator(
            export = { _, _ ->
                events += "export"
                M4aExportOutcome.Success("content://downloads/export.m4a")
            },
            isStopped = { false },
            publishProgress = { error("This success path does not publish progress") }
        ).execute(M4aExportWorkContract.encodeRequest(request)) {
            events += "foreground"
        }

        assertEquals(listOf("foreground", "export"), events)
        assertSuccess(result, request.entryId, "content://downloads/export.m4a")
    }

    @Test
    fun unsupported_returnsStableUnsupportedErrorCode() = runBlocking {
        val result = coordinator(M4aExportOutcome.Unsupported(M4aUnsupportedReason.MISSING_AUDIO))
            .execute(M4aExportWorkContract.encodeRequest(request())) {}

        assertFailure(result, "UNSUPPORTED_MISSING_AUDIO", "entry-7")
    }

    @Test
    fun useCaseFailure_preservesStableErrorCode() = runBlocking {
        val result = coordinator(M4aExportOutcome.Failure("ENGINE_OUTPUT_OPEN_FAILED"))
            .execute(M4aExportWorkContract.encodeRequest(request())) {}

        assertFailure(result, "ENGINE_OUTPUT_OPEN_FAILED", "entry-7")
    }

    @Test
    fun cancelled_throwsCancellationException() = runBlocking {
        val failure = runCatching {
            coordinator(M4aExportOutcome.Cancelled)
                .execute(M4aExportWorkContract.encodeRequest(request())) {}
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
    }

    @Test
    fun progress_isClampedAndPublishedWithM4aDataKeys() = runBlocking {
        val progressUpdates = mutableListOf<Data>()
        val result = M4aExportWorkerCoordinator(
            export = { _, listener ->
                listener.onAnalyzing()
                listener.onProgress(-4)
                listener.onProgress(132)
                assertFalse(listener.isCancelled())
                M4aExportOutcome.Success("content://downloads/export.m4a")
            },
            isStopped = { false },
            publishProgress = progressUpdates::add
        ).execute(M4aExportWorkContract.encodeRequest(request())) {}

        assertSuccess(result, "entry-7", "content://downloads/export.m4a")
        assertEquals(3, progressUpdates.size)
        assertProgress(progressUpdates[0], M4aExportWorkContract.PHASE_ANALYZING, 0)
        assertProgress(progressUpdates[1], M4aExportWorkContract.PHASE_EXPORTING, 0)
        assertProgress(progressUpdates[2], M4aExportWorkContract.PHASE_EXPORTING, 100)
    }

    @Test
    fun foregroundSpec_usesM4aChannelPositiveWorkIdAndDataSyncType() {
        val workId = UUID(0L, 0L)
        val spec = M4aExportForegroundSpec.forWork(workId)

        assertEquals("m4a_exports", spec.channelId)
        assertEquals(workId, spec.cancelWorkId)
        assertTrue(spec.notificationId > 0)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, spec.foregroundServiceType)
    }

    private fun coordinator(outcome: M4aExportOutcome): M4aExportWorkerCoordinator {
        return M4aExportWorkerCoordinator(
            export = { _, _ -> outcome },
            isStopped = { false },
            publishProgress = {}
        )
    }

    private fun request(): M4aExportRequest {
        return M4aExportRequest(
            entryId = "entry-7",
            title = "Episode 7",
            location = CacheEntryLocation.FileDirectory("/cache/entry-7")
        )
    }

    private fun assertSuccess(result: ListenableWorker.Result, entryId: String, outputUri: String) {
        assertTrue(result is ListenableWorker.Result.Success)
        val output = (result as ListenableWorker.Result.Success).outputData
        assertEquals(entryId, output.getString(M4aExportWorkContract.KEY_ENTRY_ID))
        assertEquals(outputUri, output.getString(M4aExportWorkContract.KEY_OUTPUT_URI))
    }

    private fun assertFailure(result: ListenableWorker.Result, errorCode: String, entryId: String?) {
        assertTrue(result is ListenableWorker.Result.Failure)
        val output = (result as ListenableWorker.Result.Failure).outputData
        assertEquals(errorCode, output.getString(M4aExportWorkContract.KEY_ERROR_CODE))
        if (entryId == null) {
            assertEquals(null, output.getString(M4aExportWorkContract.KEY_ENTRY_ID))
        } else {
            assertEquals(entryId, output.getString(M4aExportWorkContract.KEY_ENTRY_ID))
        }
    }

    private fun assertProgress(data: Data, phase: String, progress: Int) {
        assertEquals("entry-7", data.getString(M4aExportWorkContract.KEY_ENTRY_ID))
        assertEquals(phase, data.getString(M4aExportWorkContract.KEY_PHASE))
        assertEquals(progress, data.getInt(M4aExportWorkContract.KEY_PROGRESS, -1))
    }
}
