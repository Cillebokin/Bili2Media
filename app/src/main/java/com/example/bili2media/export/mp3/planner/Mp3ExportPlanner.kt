package com.example.bili2media.export.mp3.planner

import com.example.bili2media.export.mp3.model.Mp3ExportPlan
import com.example.bili2media.export.mp3.model.Mp3ExportPlanningResult
import com.example.bili2media.export.mp3.model.Mp3UnsupportedReason
import com.example.bili2media.export.model.MediaTrackSelection
import com.example.bili2media.media.model.MediaTrackKind
import com.example.bili2media.media.model.ProbedMediaFile
import com.example.bili2media.media.probe.MediaProbeResult
import java.util.Locale

class Mp3ExportPlanner {
    fun plan(results: List<MediaProbeResult>): Mp3ExportPlanningResult {
        if (results.isEmpty()) return unsupported(Mp3UnsupportedReason.NO_MEDIA)

        val mediaFiles = results.mapNotNull { (it as? MediaProbeResult.Success)?.media }
        if (mediaFiles.isEmpty()) return unsupported(Mp3UnsupportedReason.PROBE_FAILED)

        val analysis = PlanningAnalysis()
        mediaFiles.sortedWith(mediaPathComparator()).forEach { media -> analyze(media, analysis) }
        val selected = analysis.candidates.sortedWith(candidateComparator()).firstOrNull()
        return selected?.let { Mp3ExportPlanningResult.Ready(Mp3ExportPlan(it.selection)) }
            ?: unsupported(analysis.bestUnsupportedReason())
    }

    private fun analyze(media: ProbedMediaFile, analysis: PlanningAnalysis) {
        val extension = media.file.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension !in SUPPORTED_EXTENSIONS) return

        val audioTracks = media.tracks.filter { it.kind == MediaTrackKind.AUDIO }
        if (audioTracks.size > 1) {
            analysis.ambiguousTracks = true
            return
        }
        val audio = audioTracks.singleOrNull()
        if (audio == null) {
            analysis.missingAudio = true
            return
        }
        if (!audio.mimeType.equals(AAC_MIME, ignoreCase = true)) {
            analysis.unsupportedCodec = true
            return
        }

        analysis.candidates += Candidate(
            selection = MediaTrackSelection(
                input = media.file.input,
                trackIndex = audio.index,
                durationUs = audio.durationUs?.coerceAtLeast(0L) ?: 0L
            ),
            bitrate = audio.bitrate?.coerceAtLeast(0) ?: 0,
            sampleRate = audio.sampleRate?.coerceAtLeast(0) ?: 0,
            channelCount = audio.channelCount?.coerceAtLeast(0) ?: 0,
            stablePath = media.file.relativePath
        )
    }

    private fun mediaPathComparator(): Comparator<ProbedMediaFile> =
        compareBy<ProbedMediaFile> { it.file.relativePath.lowercase(Locale.ROOT) }
            .thenBy { it.file.relativePath }

    private fun candidateComparator(): Comparator<Candidate> =
        compareByDescending<Candidate> { it.bitrate }
            .thenByDescending { it.sampleRate }
            .thenByDescending { it.channelCount }
            .thenBy { it.stablePath.lowercase(Locale.ROOT) }
            .thenBy { it.stablePath }

    private fun unsupported(reason: Mp3UnsupportedReason) =
        Mp3ExportPlanningResult.Unsupported(reason)

    private data class Candidate(
        val selection: MediaTrackSelection,
        val bitrate: Int,
        val sampleRate: Int,
        val channelCount: Int,
        val stablePath: String
    )

    private data class PlanningAnalysis(
        val candidates: MutableList<Candidate> = mutableListOf(),
        var ambiguousTracks: Boolean = false,
        var unsupportedCodec: Boolean = false,
        var missingAudio: Boolean = false
    ) {
        fun bestUnsupportedReason(): Mp3UnsupportedReason = when {
            ambiguousTracks -> Mp3UnsupportedReason.AMBIGUOUS_TRACKS
            unsupportedCodec -> Mp3UnsupportedReason.UNSUPPORTED_CODEC
            missingAudio -> Mp3UnsupportedReason.MISSING_AUDIO
            else -> Mp3UnsupportedReason.NO_MEDIA
        }
    }

    private companion object {
        const val AAC_MIME = "audio/mp4a-latm"
        val SUPPORTED_EXTENSIONS = setOf("m4s", "mp4", "m4a")
    }
}
