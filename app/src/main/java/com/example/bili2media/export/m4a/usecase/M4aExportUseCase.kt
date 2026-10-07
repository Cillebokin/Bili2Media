package com.example.bili2media.export.m4a.usecase

import com.example.bili2media.cache.media.CacheMediaLocator
import com.example.bili2media.cache.model.CacheMediaFile
import com.example.bili2media.export.m4a.engine.M4aEngineListener
import com.example.bili2media.export.m4a.engine.M4aEngineResult
import com.example.bili2media.export.m4a.engine.M4aExportEngine
import com.example.bili2media.export.m4a.model.M4aExportPlanningResult
import com.example.bili2media.export.m4a.output.M4aOutputStore
import com.example.bili2media.export.m4a.output.M4aPendingOutput
import com.example.bili2media.export.m4a.planner.M4aExportPlanner
import com.example.bili2media.media.probe.MediaProbe
import com.example.bili2media.media.probe.MediaProbeResult
import java.util.Locale

interface M4aExportListener : M4aEngineListener {
    fun onAnalyzing()
}

internal fun interface M4aPlanningDelegate {
    fun plan(results: List<MediaProbeResult>): M4aExportPlanningResult
}

class M4aExportUseCase private constructor(
    private val mediaLocator: CacheMediaLocator,
    private val mediaProbe: MediaProbe,
    private val planner: M4aPlanningDelegate,
    private val outputStore: M4aOutputStore,
    private val engine: M4aExportEngine
) {
    constructor(
        mediaLocator: CacheMediaLocator,
        mediaProbe: MediaProbe,
        planner: M4aExportPlanner,
        outputStore: M4aOutputStore,
        engine: M4aExportEngine
    ) : this(
        mediaLocator = mediaLocator,
        mediaProbe = mediaProbe,
        planner = M4aPlanningDelegate(planner::plan),
        outputStore = outputStore,
        engine = engine
    )

    fun execute(
        request: M4aExportRequest,
        listener: M4aExportListener
    ): M4aExportOutcome {
        cancellationOutcome(listener)?.let { return it }
        try {
            listener.onAnalyzing()
        } catch (_: Exception) {
            return M4aExportOutcome.Failure(LISTENER_FAILED)
        }

        val files = try {
            mediaLocator.locate(request.location)
                .filter { it.isM4aExportCandidate() }
        } catch (_: Exception) {
            return M4aExportOutcome.Failure(LOCATE_FAILED)
        }
        cancellationOutcome(listener)?.let { return it }

        val probeResults = try {
            files.map(mediaProbe::probe)
        } catch (_: Exception) {
            return M4aExportOutcome.Failure(PROBE_FAILED)
        }
        cancellationOutcome(listener)?.let { return it }

        val planningResult = try {
            planner.plan(probeResults)
        } catch (_: Exception) {
            return M4aExportOutcome.Failure(PLANNING_FAILED)
        }
        val exportPlan = when (planningResult) {
            is M4aExportPlanningResult.Ready -> planningResult.plan
            is M4aExportPlanningResult.Unsupported -> {
                return M4aExportOutcome.Unsupported(planningResult.reason)
            }
        }

        val output = try {
            outputStore.create(request.title)
        } catch (_: Exception) {
            return M4aExportOutcome.Failure(OUTPUT_CREATE_FAILED)
        }
        cancellationOutcome(listener)?.let { return abandonThen(output, it) }

        val engineResult = try {
            engine.export(exportPlan, output.uri, listener)
        } catch (_: Exception) {
            return abandonThen(output, M4aExportOutcome.Failure(ENGINE_EXCEPTION))
        }
        return when (engineResult) {
            M4aEngineResult.Success -> {
                cancellationOutcome(listener)?.let { return abandonThen(output, it) }
                commit(output)
            }
            M4aEngineResult.Cancelled -> abandonThen(output, M4aExportOutcome.Cancelled)
            is M4aEngineResult.Failure -> abandonThen(
                output,
                M4aExportOutcome.Failure("ENGINE_${engineResult.error.name}")
            )
        }
    }

    private fun commit(output: M4aPendingOutput): M4aExportOutcome {
        return try {
            outputStore.commit(output)
            M4aExportOutcome.Success(output.uri)
        } catch (_: Exception) {
            abandonThen(output, M4aExportOutcome.Failure(OUTPUT_COMMIT_FAILED))
        }
    }

    private fun abandonThen(
        output: M4aPendingOutput,
        outcome: M4aExportOutcome
    ): M4aExportOutcome {
        return try {
            outputStore.abandon(output)
            outcome
        } catch (_: Exception) {
            M4aExportOutcome.Failure(OUTPUT_CLEANUP_FAILED)
        }
    }

    private fun cancellationOutcome(listener: M4aExportListener): M4aExportOutcome? {
        return try {
            if (listener.isCancelled()) M4aExportOutcome.Cancelled else null
        } catch (_: Exception) {
            M4aExportOutcome.Failure(LISTENER_FAILED)
        }
    }

    private fun CacheMediaFile.isM4aExportCandidate(): Boolean {
        return name.substringAfterLast('.', "")
            .lowercase(Locale.ROOT) in SUPPORTED_EXTENSIONS
    }

    companion object Testing {
        val SUPPORTED_EXTENSIONS = setOf("m4s", "mp4", "m4a")
        const val LISTENER_FAILED = "LISTENER_FAILED"
        const val LOCATE_FAILED = "LOCATE_FAILED"
        const val PROBE_FAILED = "PROBE_FAILED"
        const val PLANNING_FAILED = "PLANNING_FAILED"
        const val OUTPUT_CREATE_FAILED = "OUTPUT_CREATE_FAILED"
        const val ENGINE_EXCEPTION = "ENGINE_EXCEPTION"
        const val OUTPUT_COMMIT_FAILED = "OUTPUT_COMMIT_FAILED"
        const val OUTPUT_CLEANUP_FAILED = "OUTPUT_CLEANUP_FAILED"

        internal fun create(
            mediaLocator: CacheMediaLocator,
            mediaProbe: MediaProbe,
            planner: M4aPlanningDelegate,
            outputStore: M4aOutputStore,
            engine: M4aExportEngine
        ): M4aExportUseCase {
            return M4aExportUseCase(
                mediaLocator = mediaLocator,
                mediaProbe = mediaProbe,
                planner = planner,
                outputStore = outputStore,
                engine = engine
            )
        }
    }
}
