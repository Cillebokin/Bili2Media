package com.example.bili2media.export.mp3.usecase

import com.example.bili2media.cache.media.CacheMediaLocator
import com.example.bili2media.cache.model.CacheMediaFile
import com.example.bili2media.export.mp3.engine.Mp3EngineListener
import com.example.bili2media.export.mp3.engine.Mp3EngineResult
import com.example.bili2media.export.mp3.engine.Mp3ExportEngine
import com.example.bili2media.export.mp3.model.Mp3ExportPlanningResult
import com.example.bili2media.export.mp3.output.Mp3OutputStore
import com.example.bili2media.export.mp3.output.Mp3PendingOutput
import com.example.bili2media.export.mp3.planner.Mp3ExportPlanner
import com.example.bili2media.media.probe.MediaProbe
import java.util.Locale

interface Mp3ExportListener : Mp3EngineListener {
    fun onAnalyzing()
}

class Mp3ExportUseCase(
    private val mediaLocator: CacheMediaLocator,
    private val mediaProbe: MediaProbe,
    private val planner: Mp3ExportPlanner,
    private val outputStore: Mp3OutputStore,
    private val engine: Mp3ExportEngine
) {
    fun execute(request: Mp3ExportRequest, listener: Mp3ExportListener): Mp3ExportOutcome {
        cancellationOutcome(listener)?.let { return it }
        try {
            listener.onAnalyzing()
        } catch (_: Exception) {
            return Mp3ExportOutcome.Failure(LISTENER_FAILED)
        }

        val files = try {
            mediaLocator.locate(request.location).filter { it.isMp3ExportCandidate() }
        } catch (_: Exception) {
            return Mp3ExportOutcome.Failure(LOCATE_FAILED)
        }
        cancellationOutcome(listener)?.let { return it }

        val probeResults = try {
            files.map(mediaProbe::probe)
        } catch (_: Exception) {
            return Mp3ExportOutcome.Failure(PROBE_FAILED)
        }
        cancellationOutcome(listener)?.let { return it }

        val planningResult = try {
            planner.plan(probeResults)
        } catch (_: Exception) {
            return Mp3ExportOutcome.Failure(PLANNING_FAILED)
        }
        val plan = when (planningResult) {
            is Mp3ExportPlanningResult.Ready -> planningResult.plan
            is Mp3ExportPlanningResult.Unsupported -> {
                return Mp3ExportOutcome.Unsupported(planningResult.reason)
            }
        }

        val output = try {
            outputStore.create(request.title)
        } catch (_: Exception) {
            return Mp3ExportOutcome.Failure(OUTPUT_CREATE_FAILED)
        }
        cancellationOutcome(listener)?.let { return abandonThen(output, it) }

        val engineResult = try {
            engine.export(plan, output.uri, listener)
        } catch (_: Exception) {
            return abandonThen(output, Mp3ExportOutcome.Failure(ENGINE_EXCEPTION))
        }
        return when (engineResult) {
            Mp3EngineResult.Success -> {
                cancellationOutcome(listener)?.let { return abandonThen(output, it) }
                commit(output)
            }

            Mp3EngineResult.Cancelled -> abandonThen(output, Mp3ExportOutcome.Cancelled)
            is Mp3EngineResult.Failure -> abandonThen(
                output,
                Mp3ExportOutcome.Failure("ENGINE_${engineResult.error.name}")
            )
        }
    }

    private fun commit(output: Mp3PendingOutput): Mp3ExportOutcome = try {
        outputStore.commit(output)
        Mp3ExportOutcome.Success(output.uri)
    } catch (_: Exception) {
        abandonThen(output, Mp3ExportOutcome.Failure(OUTPUT_COMMIT_FAILED))
    }

    private fun abandonThen(
        output: Mp3PendingOutput,
        outcome: Mp3ExportOutcome
    ): Mp3ExportOutcome = try {
        outputStore.abandon(output)
        outcome
    } catch (_: Exception) {
        Mp3ExportOutcome.Failure(OUTPUT_CLEANUP_FAILED)
    }

    private fun cancellationOutcome(listener: Mp3ExportListener): Mp3ExportOutcome? = try {
        if (listener.isCancelled()) Mp3ExportOutcome.Cancelled else null
    } catch (_: Exception) {
        Mp3ExportOutcome.Failure(LISTENER_FAILED)
    }

    private fun CacheMediaFile.isMp3ExportCandidate(): Boolean =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT) in SUPPORTED_EXTENSIONS

    companion object {
        val SUPPORTED_EXTENSIONS = setOf("m4s", "mp4", "m4a")
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
