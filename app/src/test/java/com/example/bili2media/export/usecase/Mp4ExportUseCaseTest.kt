package com.example.bili2media.export.usecase

import com.example.bili2media.cache.media.CacheMediaLocator
import com.example.bili2media.cache.model.CacheEntryLocation
import com.example.bili2media.cache.model.CacheMediaFile
import com.example.bili2media.cache.model.MediaInputRef
import com.example.bili2media.export.engine.Mp4EngineError
import com.example.bili2media.export.engine.Mp4EngineResult
import com.example.bili2media.export.engine.Mp4ExportEngine
import com.example.bili2media.export.model.Mp4ExportPlan
import com.example.bili2media.export.model.Mp4UnsupportedReason
import com.example.bili2media.export.output.Mp4OutputStore
import com.example.bili2media.export.output.Mp4PendingOutput
import com.example.bili2media.export.planner.Mp4ExportPlanner
import com.example.bili2media.media.model.MediaTrackInfo
import com.example.bili2media.media.model.MediaTrackKind
import com.example.bili2media.media.model.ProbedMediaFile
import com.example.bili2media.media.probe.MediaProbe
import com.example.bili2media.media.probe.MediaProbeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Mp4ExportUseCaseTest {
    private val request = Mp4ExportRequest(
        entryId = "entry-1",
        title = "Episode",
        location = CacheEntryLocation.FileDirectory("/cache/entry-1")
    )
    private val mediaFile = CacheMediaFile(
        name = "episode.mp4",
        relativePath = "episode.mp4",
        size = 1_024L,
        input = MediaInputRef.FilePath("/cache/entry-1/episode.mp4")
    )
    private val validProbe = MediaProbeResult.Success(
        ProbedMediaFile(
            file = mediaFile,
            tracks = listOf(
                track(0, MediaTrackKind.VIDEO, "video/avc", 1920, 1080),
                track(1, MediaTrackKind.AUDIO, "audio/mp4a-latm")
            )
        )
    )

    @Test
    fun execute_commitsSuccessfulOutputExactlyOnce() {
        val outputStore = FakeOutputStore()
        val useCase = useCase(
            outputStore = outputStore,
            engine = FakeEngine(Mp4EngineResult.Success)
        )

        val outcome = useCase.execute(request, FakeListener())

        assertEquals(Mp4ExportOutcome.Success("content://output/1"), outcome)
        assertEquals(1, outputStore.createCount)
        assertEquals(listOf("commit:content://output/1"), outputStore.actions)
    }

    @Test
    fun execute_doesNotCreateOutputWhenPlanningIsUnsupported() {
        val outputStore = FakeOutputStore()
        val useCase = Mp4ExportUseCase(
            mediaLocator = FakeLocator(emptyList()),
            mediaProbe = FakeProbe(validProbe),
            planner = Mp4ExportPlanner(),
            outputStore = outputStore,
            engine = FakeEngine(Mp4EngineResult.Success)
        )

        val outcome = useCase.execute(request, FakeListener())

        assertEquals(
            Mp4ExportOutcome.Unsupported(Mp4UnsupportedReason.NO_MEDIA),
            outcome
        )
        assertEquals(0, outputStore.createCount)
        assertTrue(outputStore.actions.isEmpty())
    }

    @Test
    fun execute_abandonsOutputWhenEngineFails() {
        val outputStore = FakeOutputStore()
        val useCase = useCase(
            outputStore = outputStore,
            engine = FakeEngine(
                Mp4EngineResult.Failure(Mp4EngineError.MUXER_FAILED)
            )
        )

        val outcome = useCase.execute(request, FakeListener())

        assertEquals(Mp4ExportOutcome.Failure("ENGINE_MUXER_FAILED"), outcome)
        assertEquals(listOf("abandon:content://output/1"), outputStore.actions)
    }

    @Test
    fun execute_abandonsOutputAndReturnsCancelled() {
        val outputStore = FakeOutputStore()
        val useCase = useCase(
            outputStore = outputStore,
            engine = FakeEngine(Mp4EngineResult.Cancelled)
        )

        val outcome = useCase.execute(request, FakeListener())

        assertEquals(Mp4ExportOutcome.Cancelled, outcome)
        assertEquals(listOf("abandon:content://output/1"), outputStore.actions)
    }

    @Test
    fun execute_abandonsOutputWhenExceptionOccursAfterCreation() {
        val outputStore = FakeOutputStore()
        val useCase = useCase(
            outputStore = outputStore,
            engine = FakeEngine(error = IllegalStateException("boom"))
        )

        val outcome = useCase.execute(request, FakeListener())

        assertEquals(Mp4ExportOutcome.Failure("ENGINE_EXCEPTION"), outcome)
        assertEquals(listOf("abandon:content://output/1"), outputStore.actions)
    }

    @Test
    fun execute_returnsStableLocatorAndProbeFailureCodes() {
        val locatorFailure = Mp4ExportUseCase(
            mediaLocator = FakeLocator(error = IllegalStateException("locate")),
            mediaProbe = FakeProbe(validProbe),
            planner = Mp4ExportPlanner(),
            outputStore = FakeOutputStore(),
            engine = FakeEngine(Mp4EngineResult.Success)
        )
        val probeFailure = Mp4ExportUseCase(
            mediaLocator = FakeLocator(listOf(mediaFile)),
            mediaProbe = FakeProbe(validProbe, IllegalStateException("probe")),
            planner = Mp4ExportPlanner(),
            outputStore = FakeOutputStore(),
            engine = FakeEngine(Mp4EngineResult.Success)
        )

        assertEquals(
            Mp4ExportOutcome.Failure("LOCATE_FAILED"),
            locatorFailure.execute(request, FakeListener())
        )
        assertEquals(
            Mp4ExportOutcome.Failure("PROBE_FAILED"),
            probeFailure.execute(request, FakeListener())
        )
    }

    private fun useCase(
        outputStore: FakeOutputStore,
        engine: FakeEngine
    ): Mp4ExportUseCase {
        return Mp4ExportUseCase(
            mediaLocator = FakeLocator(listOf(mediaFile)),
            mediaProbe = FakeProbe(validProbe),
            planner = Mp4ExportPlanner(),
            outputStore = outputStore,
            engine = engine
        )
    }

    private fun track(
        index: Int,
        kind: MediaTrackKind,
        mimeType: String,
        width: Int? = null,
        height: Int? = null
    ): MediaTrackInfo {
        return MediaTrackInfo(
            index = index,
            kind = kind,
            mimeType = mimeType,
            durationUs = 10_000_000L,
            width = width,
            height = height,
            bitrate = 4_000_000
        )
    }

    private class FakeLocator(
        private val files: List<CacheMediaFile> = emptyList(),
        private val error: RuntimeException? = null
    ) : CacheMediaLocator {
        override fun locate(location: CacheEntryLocation): List<CacheMediaFile> {
            error?.let { throw it }
            return files
        }
    }

    private class FakeProbe(
        private val result: MediaProbeResult,
        private val error: RuntimeException? = null
    ) : MediaProbe {
        override fun probe(file: CacheMediaFile): MediaProbeResult {
            error?.let { throw it }
            return result
        }
    }

    private class FakeOutputStore : Mp4OutputStore {
        private val output = Mp4PendingOutput("content://output/1", "Episode.mp4")
        var createCount = 0
        val actions = mutableListOf<String>()

        override fun create(title: String): Mp4PendingOutput {
            createCount++
            return output
        }

        override fun commit(output: Mp4PendingOutput) {
            actions += "commit:${output.uri}"
        }

        override fun abandon(output: Mp4PendingOutput) {
            actions += "abandon:${output.uri}"
        }
    }

    private class FakeEngine(
        private val result: Mp4EngineResult = Mp4EngineResult.Success,
        private val error: RuntimeException? = null
    ) : Mp4ExportEngine {
        var receivedPlan: Mp4ExportPlan? = null

        override fun export(
            plan: Mp4ExportPlan,
            outputUri: String,
            listener: com.example.bili2media.export.engine.Mp4EngineListener
        ): Mp4EngineResult {
            error?.let { throw it }
            receivedPlan = plan
            return result
        }
    }

    private class FakeListener : Mp4ExportListener {
        var analyzingCount = 0

        override fun onAnalyzing() {
            analyzingCount++
        }

        override fun onProgress(percent: Int) = Unit

        override fun isCancelled(): Boolean = false
    }
}
