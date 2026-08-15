package com.example.bili2media.export.planner

import com.example.bili2media.export.model.MediaTrackSelection
import com.example.bili2media.export.model.Mp4ExportPlan
import com.example.bili2media.export.model.Mp4ExportPlanningResult
import com.example.bili2media.export.model.Mp4UnsupportedReason
import com.example.bili2media.media.model.MediaTrackInfo
import com.example.bili2media.media.model.MediaTrackKind
import com.example.bili2media.media.model.ProbedMediaFile
import com.example.bili2media.media.probe.MediaProbeResult
import java.util.Locale

class Mp4ExportPlanner(
    private val codecPolicy: Mp4CodecPolicy = Mp4CodecPolicy()
) {
    fun plan(results: List<MediaProbeResult>): Mp4ExportPlanningResult {
        if (results.isEmpty()) {
            return unsupported(Mp4UnsupportedReason.NO_MEDIA)
        }

        val mediaFiles = results.mapNotNull { result ->
            (result as? MediaProbeResult.Success)?.media
        }
        if (mediaFiles.isEmpty()) {
            return unsupported(Mp4UnsupportedReason.PROBE_FAILED)
        }

        val analysis = PlanningAnalysis()
        val m4sGroups = linkedMapOf<String, M4sGroup>()
        mediaFiles
            .sortedWith(mediaPathComparator())
            .forEach { media ->
                when (media.file.extension()) {
                    MP4_EXTENSION -> analyzeMp4(media, analysis)
                    M4S_EXTENSION -> analyzeM4s(media, m4sGroups, analysis)
                }
            }
        m4sGroups.values.forEach { group -> analyzeM4sGroup(group, analysis) }

        val selected = analysis.candidates
            .sortedWith(candidateComparator())
            .firstOrNull()
        if (selected != null) {
            return Mp4ExportPlanningResult.Ready(selected.plan)
        }
        return unsupported(analysis.bestUnsupportedReason())
    }

    private fun analyzeMp4(
        media: ProbedMediaFile,
        analysis: PlanningAnalysis
    ) {
        val videoTracks = media.tracks.ofKind(MediaTrackKind.VIDEO)
        val audioTracks = media.tracks.ofKind(MediaTrackKind.AUDIO)
        if (videoTracks.size > 1 || audioTracks.size > 1) {
            analysis.ambiguousTracks = true
            return
        }
        if (videoTracks.isEmpty() && audioTracks.isEmpty()) {
            return
        }
        if (videoTracks.isEmpty()) {
            analysis.missingVideo = true
            return
        }
        if (audioTracks.isEmpty()) {
            analysis.missingAudio = true
            return
        }

        val video = videoTracks.single()
        val audio = audioTracks.single()
        if (!codecPolicy.isSupportedVideo(video.mimeType) ||
            !codecPolicy.isSupportedAudio(audio.mimeType)
        ) {
            analysis.unsupportedCodec = true
            return
        }

        analysis.candidates += Candidate(
            plan = Mp4ExportPlan.CopyExistingMp4(
                input = media.file.input,
                sourceBytes = media.file.size.coerceAtLeast(0L),
                durationUs = maxOf(video.safeDuration(), audio.safeDuration())
            ),
            pixelCount = video.pixelCount(),
            videoBitrate = video.bitrate.orZero(),
            copyPriority = COPY_PRIORITY,
            stablePath = media.file.relativePath
        )
    }

    private fun analyzeM4s(
        media: ProbedMediaFile,
        groups: MutableMap<String, M4sGroup>,
        analysis: PlanningAnalysis
    ) {
        val videoTracks = media.tracks.ofKind(MediaTrackKind.VIDEO)
        val audioTracks = media.tracks.ofKind(MediaTrackKind.AUDIO)
        if (videoTracks.size > 1 || audioTracks.size > 1 ||
            videoTracks.isNotEmpty() && audioTracks.isNotEmpty()
        ) {
            analysis.ambiguousTracks = true
            return
        }

        val parent = media.file.parentPath()
        val group = groups.getOrPut(parent) { M4sGroup(parent) }
        when {
            videoTracks.size == 1 -> {
                val track = videoTracks.single()
                if (codecPolicy.isSupportedVideo(track.mimeType)) {
                    group.videos += TrackFile(media, track)
                } else {
                    analysis.unsupportedCodec = true
                }
            }

            audioTracks.size == 1 -> {
                val track = audioTracks.single()
                if (codecPolicy.isSupportedAudio(track.mimeType)) {
                    group.audios += TrackFile(media, track)
                } else {
                    analysis.unsupportedCodec = true
                }
            }
        }
    }

    private fun analyzeM4sGroup(
        group: M4sGroup,
        analysis: PlanningAnalysis
    ) {
        if (group.videos.size > 1 || group.audios.size > 1) {
            analysis.ambiguousTracks = true
            return
        }
        if (group.videos.isEmpty() && group.audios.isEmpty()) {
            return
        }
        if (group.videos.isEmpty()) {
            analysis.missingVideo = true
            return
        }
        if (group.audios.isEmpty()) {
            analysis.missingAudio = true
            return
        }

        val video = group.videos.single()
        val audio = group.audios.single()
        val videoDuration = video.track.safeDuration()
        val audioDuration = audio.track.safeDuration()
        analysis.candidates += Candidate(
            plan = Mp4ExportPlan.MuxM4s(
                video = MediaTrackSelection(
                    input = video.media.file.input,
                    trackIndex = video.track.index,
                    durationUs = videoDuration
                ),
                audio = MediaTrackSelection(
                    input = audio.media.file.input,
                    trackIndex = audio.track.index,
                    durationUs = audioDuration
                ),
                durationUs = maxOf(videoDuration, audioDuration)
            ),
            pixelCount = video.track.pixelCount(),
            videoBitrate = video.track.bitrate.orZero(),
            copyPriority = MUX_PRIORITY,
            stablePath = listOf(
                group.parent,
                video.media.file.relativePath,
                audio.media.file.relativePath
            ).joinToString("|")
        )
    }

    private fun mediaPathComparator(): Comparator<ProbedMediaFile> {
        return compareBy<ProbedMediaFile> {
            it.file.relativePath.lowercase(Locale.ROOT)
        }.thenBy { it.file.relativePath }
    }

    private fun candidateComparator(): Comparator<Candidate> {
        return compareByDescending<Candidate> { it.pixelCount }
            .thenByDescending { it.videoBitrate }
            .thenByDescending { it.copyPriority }
            .thenBy { it.stablePath.lowercase(Locale.ROOT) }
            .thenBy { it.stablePath }
    }

    private fun List<MediaTrackInfo>.ofKind(kind: MediaTrackKind): List<MediaTrackInfo> {
        return filter { it.kind == kind }
    }

    private fun MediaTrackInfo.safeDuration(): Long {
        return durationUs?.coerceAtLeast(0L) ?: 0L
    }

    private fun MediaTrackInfo.pixelCount(): Long {
        return width.orZero().toLong() * height.orZero().toLong()
    }

    private fun Int?.orZero(): Int {
        return this?.coerceAtLeast(0) ?: 0
    }

    private fun com.example.bili2media.cache.model.CacheMediaFile.extension(): String {
        return name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    }

    private fun com.example.bili2media.cache.model.CacheMediaFile.parentPath(): String {
        return relativePath.substringBeforeLast('/', "")
    }

    private fun unsupported(reason: Mp4UnsupportedReason): Mp4ExportPlanningResult.Unsupported {
        return Mp4ExportPlanningResult.Unsupported(reason)
    }

    private data class TrackFile(
        val media: ProbedMediaFile,
        val track: MediaTrackInfo
    )

    private data class M4sGroup(
        val parent: String,
        val videos: MutableList<TrackFile> = mutableListOf(),
        val audios: MutableList<TrackFile> = mutableListOf()
    )

    private data class Candidate(
        val plan: Mp4ExportPlan,
        val pixelCount: Long,
        val videoBitrate: Int,
        val copyPriority: Int,
        val stablePath: String
    )

    private data class PlanningAnalysis(
        val candidates: MutableList<Candidate> = mutableListOf(),
        var ambiguousTracks: Boolean = false,
        var multiSegment: Boolean = false,
        var unsupportedCodec: Boolean = false,
        var missingVideo: Boolean = false,
        var missingAudio: Boolean = false
    ) {
        fun bestUnsupportedReason(): Mp4UnsupportedReason {
            return when {
                ambiguousTracks -> Mp4UnsupportedReason.AMBIGUOUS_TRACKS
                multiSegment -> Mp4UnsupportedReason.MULTI_SEGMENT
                unsupportedCodec -> Mp4UnsupportedReason.UNSUPPORTED_CODEC
                missingAudio -> Mp4UnsupportedReason.MISSING_AUDIO
                missingVideo -> Mp4UnsupportedReason.MISSING_VIDEO
                else -> Mp4UnsupportedReason.NO_MEDIA
            }
        }
    }

    private companion object {
        const val MP4_EXTENSION = "mp4"
        const val M4S_EXTENSION = "m4s"
        const val COPY_PRIORITY = 1
        const val MUX_PRIORITY = 0
    }
}
