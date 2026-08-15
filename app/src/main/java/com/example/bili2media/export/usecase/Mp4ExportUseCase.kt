package com.example.bili2media.export.usecase

import com.example.bili2media.cache.media.CacheMediaLocator
import com.example.bili2media.cache.model.CacheMediaFile
import com.example.bili2media.export.engine.Mp4EngineListener
import com.example.bili2media.export.engine.Mp4EngineResult
import com.example.bili2media.export.engine.Mp4ExportEngine
import com.example.bili2media.export.model.Mp4ExportPlanningResult
import com.example.bili2media.export.output.Mp4OutputStore
import com.example.bili2media.export.output.Mp4PendingOutput
import com.example.bili2media.export.planner.Mp4ExportPlanner
import com.example.bili2media.media.probe.MediaProbe
import java.util.Locale

interface Mp4ExportListener : Mp4EngineListener {
    fun onAnalyzing()
}

class Mp4ExportUseCase(
    private val mediaLocator: CacheMediaLocator,
    private val mediaProbe: MediaProbe,
    private val planner: Mp4ExportPlanner,
    private val outputStore: Mp4OutputStore,
    private val engine: Mp4ExportEngine
) {
    fun execute(
        request: Mp4ExportRequest,
        listener: Mp4ExportListener
    ): Mp4ExportOutcome {
        if (listener.isCancelled()) {
            return Mp4ExportOutcome.Cancelled
        }
        try {
            listener.onAnalyzing()
        } catch (_: Exception) {
            return Mp4ExportOutcome.Failure(LISTENER_FAILED)
        }

        val files = try {
            mediaLocator.locate(request.location)
                .filter { it.isMp4ExportCandidate() }
        } catch (_: Exception) {
            return Mp4ExportOutcome.Failure(LOCATE_FAILED)
        }
        if (listener.isCancelled()) {
            return Mp4ExportOutcome.Cancelled
        }

        val probeResults = try {
            files.map(mediaProbe::probe)
        } catch (_: Exception) {
            return Mp4ExportOutcome.Failure(PROBE_FAILED)
        }
        if (listener.isCancelled()) {
            return Mp4ExportOutcome.Cancelled
        }

        val planningResult = try {
            planner.plan(probeResults)
        } catch (_: Exception) {
            return Mp4ExportOutcome.Failure(PLANNING_FAILED)
        }
        val plan = when (planningResult) {
            is Mp4ExportPlanningResult.Ready -> planningResult.plan
            is Mp4ExportPlanningResult.Unsupported -> {
                return Mp4ExportOutcome.Unsupported(planningResult.reason)
            }
        }

        val output = try {
            outputStore.create(request.title)
        } catch (_: Exception) {
            return Mp4ExportOutcome.Failure(OUTPUT_CREATE_FAILED)
        }
        if (listener.isCancelled()) {
            return abandonThen(output, Mp4ExportOutcome.Cancelled)
        }

        val engineResult = try {
            engine.export(plan, output.uri, listener)
        } catch (_: Exception) {
            return abandonThen(
                output,
                Mp4ExportOutcome.Failure(ENGINE_EXCEPTION)
            )
        }
        return when (engineResult) {
            Mp4EngineResult.Success -> commit(output)
            Mp4EngineResult.Cancelled -> {
                abandonThen(output, Mp4ExportOutcome.Cancelled)
            }

            is Mp4EngineResult.Failure -> {
                abandonThen(
                    output,
                    Mp4ExportOutcome.Failure("ENGINE_${engineResult.error.name}")
                )
            }
        }
    }

    private fun commit(output: Mp4PendingOutput): Mp4ExportOutcome {
        return try {
            outputStore.commit(output)
            Mp4ExportOutcome.Success(output.uri)
        } catch (_: Exception) {
            abandonThen(
                output,
                Mp4ExportOutcome.Failure(OUTPUT_COMMIT_FAILED)
            )
        }
    }

    private fun abandonThen(
        output: Mp4PendingOutput,
        outcome: Mp4ExportOutcome
    ): Mp4ExportOutcome {
        return try {
            outputStore.abandon(output)
            outcome
        } catch (_: Exception) {
            Mp4ExportOutcome.Failure(OUTPUT_CLEANUP_FAILED)
        }
    }

    private fun CacheMediaFile.isMp4ExportCandidate(): Boolean {
        return name.substringAfterLast('.', "")
            .lowercase(Locale.ROOT) in SUPPORTED_EXTENSIONS
    }

    private companion object {
        val SUPPORTED_EXTENSIONS = setOf("mp4", "m4s")
        const val LISTENER_FAILED = "LISTENER_FAILED"
        const val LOCATE_FAILED = "LOCATE_FAILED"
        const val PROBE_FAILED = "PROBE_FAILED"
        const val PLANNING_FAILED = "PLANNING_FAILED"
        const val OUTPUT_CREATE_FAILED = "OUTPUT_CREATE_FAILED"
        const val ENGINE_EXCEPTION = "ENGINE_EXCEPTION"
        const val OUTPUT_COMMIT_FAILED = "OUTPUT_COMMIT_FAILED"
        const val OUTPUT_CLEANUP_FAILED = "OUTPUT_CLEANUP_FAILED"
    }
}
