package com.example.bili2media.export.m4a.planner

import com.example.bili2media.export.m4a.model.M4aExportPlan
import com.example.bili2media.export.m4a.model.M4aExportPlanningResult
import com.example.bili2media.export.m4a.model.M4aUnsupportedReason
import com.example.bili2media.export.model.MediaTrackSelection
import com.example.bili2media.media.model.MediaTrackInfo
import com.example.bili2media.media.model.MediaTrackKind
import com.example.bili2media.media.model.ProbedMediaFile
import com.example.bili2media.media.probe.MediaProbeResult
import java.util.Locale

class M4aExportPlanner(
    private val codecPolicy: M4aCodecPolicy = M4aCodecPolicy()
) {
    fun plan(results: List<MediaProbeResult>): M4aExportPlanningResult {
        if (results.isEmpty()) {
            return unsupported(M4aUnsupportedReason.NO_MEDIA)
        }
        val mediaFiles = results.mapNotNull { (it as? MediaProbeResult.Success)?.media }
        if (mediaFiles.isEmpty()) {
            return unsupported(M4aUnsupportedReason.PROBE_FAILED)
        }

        val analysis = PlanningAnalysis()
        mediaFiles.sortedWith(mediaPathComparator()).forEach { media ->
            analyze(media, analysis)
        }
        val selected = analysis.candidates.sortedWith(candidateComparator()).firstOrNull()
        return selected?.let { M4aExportPlanningResult.Ready(it.plan) }
            ?: unsupported(analysis.bestUnsupportedReason())
    }

    private fun analyze(media: ProbedMediaFile, analysis: PlanningAnalysis) {
        val extension = media.file.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension !in SUPPORTED_EXTENSIONS) {
            return
        }
        val audioTracks = media.tracks.filter { it.kind == MediaTrackKind.AUDIO }
        if (audioTracks.size > 1) {
            analysis.ambiguousTracks = true
            return
        }
        if (audioTracks.isEmpty()) {
            analysis.missingAudio = true
            return
        }
        val audio = audioTracks.single()
        if (!codecPolicy.isSupportedAudio(audio.mimeType)) {
            analysis.unsupportedCodec = true
            return
        }
        if (extension == M4A_EXTENSION &&
            media.tracks.any { it.kind == MediaTrackKind.VIDEO }
        ) {
            analysis.ambiguousTracks = true
            return
        }

        val durationUs = audio.durationUs.safeLong()
        val isCopy = extension == M4A_EXTENSION
        val plan = if (isCopy) {
            M4aExportPlan.CopyExistingM4a(
                input = media.file.input,
                sourceBytes = media.file.size.coerceAtLeast(0L),
                durationUs = durationUs
            )
        } else {
            M4aExportPlan.RemuxAacTrack(
                MediaTrackSelection(media.file.input, audio.index, durationUs)
            )
        }
        analysis.candidates += Candidate(
            plan = plan,
            bitrate = audio.bitrate.safeInt(),
            sampleRate = audio.sampleRate.safeInt(),
            channelCount = audio.channelCount.safeInt(),
            copyPriority = if (isCopy) 1 else 0,
            stablePath = media.file.relativePath
        )
    }

    private fun mediaPathComparator(): Comparator<ProbedMediaFile> {
        return compareBy<ProbedMediaFile> { it.file.relativePath.lowercase(Locale.ROOT) }
            .thenBy { it.file.relativePath }
    }

    private fun candidateComparator(): Comparator<Candidate> {
        return compareByDescending<Candidate> { it.bitrate }
            .thenByDescending { it.sampleRate }
            .thenByDescending { it.channelCount }
            .thenByDescending { it.copyPriority }
            .thenBy { it.stablePath.lowercase(Locale.ROOT) }
            .thenBy { it.stablePath }
    }

    private fun Int?.safeInt(): Int = this?.coerceAtLeast(0) ?: 0
    private fun Long?.safeLong(): Long = this?.coerceAtLeast(0L) ?: 0L

    private fun unsupported(reason: M4aUnsupportedReason) =
        M4aExportPlanningResult.Unsupported(reason)

    private data class Candidate(
        val plan: M4aExportPlan,
        val bitrate: Int,
        val sampleRate: Int,
        val channelCount: Int,
        val copyPriority: Int,
        val stablePath: String
    )

    private data class PlanningAnalysis(
        val candidates: MutableList<Candidate> = mutableListOf(),
        var ambiguousTracks: Boolean = false,
        var unsupportedCodec: Boolean = false,
        var missingAudio: Boolean = false
    ) {
        fun bestUnsupportedReason(): M4aUnsupportedReason {
            return when {
                ambiguousTracks -> M4aUnsupportedReason.AMBIGUOUS_TRACKS
                unsupportedCodec -> M4aUnsupportedReason.UNSUPPORTED_CODEC
                missingAudio -> M4aUnsupportedReason.MISSING_AUDIO
                else -> M4aUnsupportedReason.NO_MEDIA
            }
        }
    }

    private companion object {
        const val M4A_EXTENSION = "m4a"
        val SUPPORTED_EXTENSIONS = setOf("m4a", "m4s", "mp4")
    }
}
