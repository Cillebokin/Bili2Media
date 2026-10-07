package com.example.bili2media.export.m4a.usecase

import com.example.bili2media.cache.media.CacheMediaLocator
import com.example.bili2media.cache.model.CacheEntryLocation
import com.example.bili2media.cache.model.CacheMediaFile
import com.example.bili2media.cache.model.MediaInputRef
import com.example.bili2media.export.m4a.engine.M4aEngineError
import com.example.bili2media.export.m4a.engine.M4aEngineListener
import com.example.bili2media.export.m4a.engine.M4aEngineResult
import com.example.bili2media.export.m4a.engine.M4aExportEngine
import com.example.bili2media.export.m4a.model.M4aExportPlan
import com.example.bili2media.export.m4a.model.M4aExportPlanningResult
import com.example.bili2media.export.m4a.model.M4aUnsupportedReason
import com.example.bili2media.export.m4a.output.M4aOutputStore
import com.example.bili2media.export.m4a.output.M4aPendingOutput
import com.example.bili2media.export.m4a.planner.M4aExportPlanner
import com.example.bili2media.media.model.MediaTrackInfo
import com.example.bili2media.media.model.MediaTrackKind
import com.example.bili2media.media.model.ProbedMediaFile
import com.example.bili2media.media.probe.MediaProbe
import com.example.bili2media.media.probe.MediaProbeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class M4aExportUseCaseTest {
    private val request = M4aExportRequest(
        entryId = "entry-1",
        title = "Episode",
        location = CacheEntryLocation.FileDirectory("/cache/entry-1")
    )
    private val mediaFile = mediaFile("episode.mp4")

    @Test
    fun execute_commitsSuccessfulOutputExactlyOnce() {
        val outputStore = FakeOutputStore()

        val outcome = useCase(
            outputStore = outputStore,
            engine = FakeEngine(M4aEngineResult.Success)
        ).execute(request, FakeListener())

        assertEquals(M4aExportOutcome.Success("content://output/1"), outcome)
        assertEquals(1, outputStore.createCount)
        assertEquals(listOf("commit:content://output/1"), outputStore.actions)
    }

    @Test
    fun execute_doesNotCreateOutputWhenPlanningIsUnsupported() {
        val outputStore = FakeOutputStore()

        val outcome = useCase(
            files = emptyList(),
            outputStore = outputStore
        ).execute(request, FakeListener())

        assertEquals(
            M4aExportOutcome.Unsupported(M4aUnsupportedReason.NO_MEDIA),
            outcome
        )
        assertEquals(0, outputStore.createCount)
        assertTrue(outputStore.actions.isEmpty())
    }

    @Test
    fun execute_abandonsOutputWhenEngineFails() {
        val outputStore = FakeOutputStore()

        val outcome = useCase(
            outputStore = outputStore,
            engine = FakeEngine(M4aEngineResult.Failure(M4aEngineError.MUXER_FAILED))
        ).execute(request, FakeListener())

        assertEquals(M4aExportOutcome.Failure("ENGINE_MUXER_FAILED"), outcome)
        assertEquals(listOf("abandon:content://output/1"), outputStore.actions)
    }

    @Test
    fun execute_abandonsOutputAndReturnsCancelledWhenEngineCancels() {
        val outputStore = FakeOutputStore()

        val outcome = useCase(
            outputStore = outputStore,
            engine = FakeEngine(M4aEngineResult.Cancelled)
        ).execute(request, FakeListener())

        assertEquals(M4aExportOutcome.Cancelled, outcome)
        assertEquals(listOf("abandon:content://output/1"), outputStore.actions)
    }

    @Test
    fun execute_abandonsOutputInsteadOfCommittingWhenCancelledAfterEngineSuccess() {
        val outputStore = FakeOutputStore()
        val listener = FakeListener()
        val engine = FakeEngine(beforeReturning = listener::cancelAfterEngine)

        val outcome = useCase(
            outputStore = outputStore,
            engine = engine
        ).execute(request, listener)

        assertEquals(M4aExportOutcome.Cancelled, outcome)
        assertEquals(listOf("abandon:content://output/1"), outputStore.actions)
    }

    @Test
    fun execute_abandonsOutputWhenPostEngineCancellationCheckThrows() {
        val outputStore = FakeOutputStore()
        val listener = FakeListener()
        val engine = FakeEngine(beforeReturning = listener::failCancellationAfterEngine)

        val outcome = useCase(
            outputStore = outputStore,
            engine = engine
        ).execute(request, listener)

        assertEquals(M4aExportOutcome.Failure("LISTENER_FAILED"), outcome)
        assertEquals(listOf("abandon:content://output/1"), outputStore.actions)
    }

    @Test
    fun execute_returnsCleanupFailureWhenPostEngineCancellationCheckThrows() {
        val outputStore = FakeOutputStore(abandonError = IllegalStateException("abandon"))
        val listener = FakeListener()
        val engine = FakeEngine(beforeReturning = listener::failCancellationAfterEngine)

        val outcome = useCase(
            outputStore = outputStore,
            engine = engine
        ).execute(request, listener)

        assertEquals(M4aExportOutcome.Failure("OUTPUT_CLEANUP_FAILED"), outcome)
        assertEquals(listOf("abandon:content://output/1"), outputStore.actions)
    }

    @Test
    fun execute_abandonsOutputWhenEngineThrows() {
        val outputStore = FakeOutputStore()

        val outcome = useCase(
            outputStore = outputStore,
            engine = FakeEngine(error = IllegalStateException("boom"))
        ).execute(request, FakeListener())

        assertEquals(M4aExportOutcome.Failure("ENGINE_EXCEPTION"), outcome)
        assertEquals(listOf("abandon:content://output/1"), outputStore.actions)
    }

    @Test
    fun execute_attemptsAbandonWhenCommitFails() {
        val outputStore = FakeOutputStore(commitError = IllegalStateException("commit"))

        val outcome = useCase(
            outputStore = outputStore,
            engine = FakeEngine(M4aEngineResult.Success)
        ).execute(request, FakeListener())

        assertEquals(M4aExportOutcome.Failure("OUTPUT_COMMIT_FAILED"), outcome)
        assertEquals(
            listOf("commit:content://output/1", "abandon:content://output/1"),
            outputStore.actions
        )
    }

    @Test
    fun execute_returnsCleanupFailureWhenAbandonFails() {
        val outputStore = FakeOutputStore(abandonError = IllegalStateException("abandon"))

        val outcome = useCase(
            outputStore = outputStore,
            engine = FakeEngine(M4aEngineResult.Cancelled)
        ).execute(request, FakeListener())

        assertEquals(M4aExportOutcome.Failure("OUTPUT_CLEANUP_FAILED"), outcome)
        assertEquals(listOf("abandon:content://output/1"), outputStore.actions)
    }

    @Test
    fun execute_returnsStableFailureCodesForLocatorProbePlanningAndOutputCreation() {
        val locatorFailure = useCase(locator = FakeLocator(error = IllegalStateException("locate")))
        val probeFailure = useCase(probe = FakeProbe(error = IllegalStateException("probe")))
        val planningFailure = useCase(plan = { throw IllegalStateException("plan") })
        val createFailure = useCase(
            outputStore = FakeOutputStore(createError = IllegalStateException("create"))
        )

        assertEquals(
            M4aExportOutcome.Failure("LOCATE_FAILED"),
            locatorFailure.execute(request, FakeListener())
        )
        assertEquals(
            M4aExportOutcome.Failure("PROBE_FAILED"),
            probeFailure.execute(request, FakeListener())
        )
        assertEquals(
            M4aExportOutcome.Failure("PLANNING_FAILED"),
            planningFailure.execute(request, FakeListener())
        )
        assertEquals(
            M4aExportOutcome.Failure("OUTPUT_CREATE_FAILED"),
            createFailure.execute(request, FakeListener())
        )
    }

    @Test
    fun execute_returnsListenerFailureWhenAnalyzingCallbackThrows() {
        val listener = FakeListener(analyzingError = IllegalStateException("listener"))

        val outcome = useCase().execute(request, listener)

        assertEquals(M4aExportOutcome.Failure("LISTENER_FAILED"), outcome)
    }

    @Test
    fun execute_returnsListenerFailureWithoutCreatingOutputWhenInitialCancellationCheckThrows() {
        val outputStore = FakeOutputStore()

        val outcome = useCase(outputStore = outputStore).execute(
            request,
            FakeListener(cancelErrorOnCheck = 1)
        )

        assertEquals(M4aExportOutcome.Failure("LISTENER_FAILED"), outcome)
        assertEquals(0, outputStore.createCount)
        assertTrue(outputStore.actions.isEmpty())
    }

    @Test
    fun execute_abandonsOutputWhenPostCreationCancellationCheckThrows() {
        val outputStore = FakeOutputStore()

        val outcome = useCase(outputStore = outputStore).execute(
            request,
            FakeListener(cancelErrorOnCheck = 4)
        )

        assertEquals(M4aExportOutcome.Failure("LISTENER_FAILED"), outcome)
        assertEquals(listOf("abandon:content://output/1"), outputStore.actions)
    }

    @Test
    fun execute_checksCancellationAtEveryRequiredCheckpoint() {
        val outcomes = (1..4).map { check ->
            val outputStore = FakeOutputStore()
            val outcome = useCase(outputStore = outputStore).execute(
                request,
                FakeListener(cancelOnCheck = check)
            )
            outcome to outputStore.actions
        }

        assertEquals(
            listOf(
                M4aExportOutcome.Cancelled to emptyList<String>(),
                M4aExportOutcome.Cancelled to emptyList(),
                M4aExportOutcome.Cancelled to emptyList(),
                M4aExportOutcome.Cancelled to listOf("abandon:content://output/1")
            ),
            outcomes
        )
    }

    @Test
    fun execute_doesNotProbeUnsupportedExtensions() {
        val probe = FakeProbe()
        val files = listOf(
            mediaFile("audio.m4s"),
            mediaFile("video.mp4"),
            mediaFile("metadata.json"),
            mediaFile("cover.jpg"),
            mediaFile("audio.m4a")
        )

        useCase(files = files, probe = probe).execute(request, FakeListener())

        assertEquals(
            listOf("audio.m4s", "video.mp4", "audio.m4a"),
            probe.probedNames
        )
    }

    @Test
    fun execute_acceptsCaseInsensitiveM4aCandidate() {
        val upperCaseM4a = mediaFile("episode.M4A")
        val probe = FakeProbe()
        val outputStore = FakeOutputStore()

        val outcome = useCase(
            files = listOf(upperCaseM4a),
            probe = probe,
            outputStore = outputStore
        ).execute(request, FakeListener())

        assertEquals(M4aExportOutcome.Success("content://output/1"), outcome)
        assertEquals(listOf("episode.M4A"), probe.probedNames)
    }

    @Test
    fun execute_passesAllProbeResultsToPlannerAndPlanOutputAndListenerToEngine() {
        val first = mediaFile("first.mp4")
        val second = mediaFile("second.m4a")
        val probe = FakeProbe()
        val engine = FakeEngine()
        val listener = FakeListener()
        val expectedPlan = M4aExportPlan.CopyExistingM4a(
            input = second.input,
            sourceBytes = second.size,
            durationUs = 10_000_000L
        )
        var plannerResults: List<MediaProbeResult>? = null

        val outcome = useCase(
            files = listOf(first, second),
            probe = probe,
            plan = { results ->
                plannerResults = results
                M4aExportPlanningResult.Ready(expectedPlan)
            },
            engine = engine
        ).execute(request, listener)

        assertEquals(M4aExportOutcome.Success("content://output/1"), outcome)
        assertEquals(probe.probeResults, plannerResults)
        assertEquals(expectedPlan, engine.receivedPlan)
        assertEquals("content://output/1", engine.receivedOutputUri)
        assertSame(listener, engine.receivedListener)
    }

    private fun useCase(
        files: List<CacheMediaFile> = listOf(mediaFile),
        locator: FakeLocator = FakeLocator(files),
        probe: FakeProbe = FakeProbe(),
        plan: ((List<MediaProbeResult>) -> M4aExportPlanningResult)? = null,
        outputStore: FakeOutputStore = FakeOutputStore(),
        engine: FakeEngine = FakeEngine(M4aEngineResult.Success)
    ): M4aExportUseCase {
        return plan?.let {
            M4aExportUseCase.Testing.create(
                mediaLocator = locator,
                mediaProbe = probe,
                planner = M4aPlanningDelegate(it),
                outputStore = outputStore,
                engine = engine
            )
        } ?: M4aExportUseCase(
            mediaLocator = locator,
            mediaProbe = probe,
            planner = M4aExportPlanner(),
            outputStore = outputStore,
            engine = engine
        )
    }

    private fun mediaFile(name: String): CacheMediaFile {
        return CacheMediaFile(
            name = name,
            relativePath = name,
            size = 1_024L,
            input = MediaInputRef.FilePath("/cache/entry-1/$name")
        )
    }

    private fun probeResult(file: CacheMediaFile): MediaProbeResult {
        return MediaProbeResult.Success(
            ProbedMediaFile(
                file = file,
                tracks = listOf(
                    MediaTrackInfo(
                        index = 0,
                        kind = MediaTrackKind.AUDIO,
                        mimeType = "audio/mp4a-latm",
                        durationUs = 10_000_000L,
                        width = null,
                        height = null,
                        bitrate = 128_000
                    )
                )
            )
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

    private inner class FakeProbe(
        private val error: RuntimeException? = null
    ) : MediaProbe {
        val probedNames = mutableListOf<String>()
        val probeResults = mutableListOf<MediaProbeResult>()

        override fun probe(file: CacheMediaFile): MediaProbeResult {
            error?.let { throw it }
            probedNames += file.name
            return probeResult(file).also(probeResults::add)
        }
    }

    private class FakeOutputStore(
        private val createError: RuntimeException? = null,
        private val commitError: RuntimeException? = null,
        private val abandonError: RuntimeException? = null
    ) : M4aOutputStore {
        private val output = M4aPendingOutput("content://output/1", "Episode.m4a")
        var createCount = 0
        val actions = mutableListOf<String>()

        override fun create(title: String): M4aPendingOutput {
            createCount++
            createError?.let { throw it }
            return output
        }

        override fun commit(output: M4aPendingOutput) {
            actions += "commit:${output.uri}"
            commitError?.let { throw it }
        }

        override fun abandon(output: M4aPendingOutput) {
            actions += "abandon:${output.uri}"
            abandonError?.let { throw it }
        }
    }

    private class FakeEngine(
        private val result: M4aEngineResult = M4aEngineResult.Success,
        private val error: RuntimeException? = null,
        private val beforeReturning: (() -> Unit)? = null
    ) : M4aExportEngine {
        var receivedPlan: M4aExportPlan? = null
        var receivedOutputUri: String? = null
        var receivedListener: M4aEngineListener? = null

        override fun export(
            plan: M4aExportPlan,
            outputUri: String,
            listener: M4aEngineListener
        ): M4aEngineResult {
            error?.let { throw it }
            receivedPlan = plan
            receivedOutputUri = outputUri
            receivedListener = listener
            beforeReturning?.invoke()
            return result
        }
    }

    private class FakeListener(
        private val analyzingError: RuntimeException? = null,
        private val cancelOnCheck: Int? = null,
        private val cancelErrorOnCheck: Int? = null
    ) : M4aExportListener {
        private var cancellationChecks = 0
        private var cancelledAfterEngine = false
        private var cancellationErrorAfterEngine = false

        fun cancelAfterEngine() {
            cancelledAfterEngine = true
        }

        fun failCancellationAfterEngine() {
            cancellationErrorAfterEngine = true
        }

        override fun onAnalyzing() {
            analyzingError?.let { throw it }
        }

        override fun onProgress(percent: Int) = Unit

        override fun isCancelled(): Boolean {
            cancellationChecks++
            if (cancellationChecks == cancelErrorOnCheck) {
                throw IllegalStateException("cancel")
            }
            if (cancellationErrorAfterEngine) {
                throw IllegalStateException("post-engine cancel")
            }
            if (cancelledAfterEngine) {
                return true
            }
            return cancellationChecks == cancelOnCheck
        }
    }
}
