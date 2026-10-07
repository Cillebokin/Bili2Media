package com.example.bili2media.export.m4a.planner

import com.example.bili2media.cache.model.CacheMediaFile
import com.example.bili2media.cache.model.MediaInputRef
import com.example.bili2media.export.m4a.model.M4aExportPlan
import com.example.bili2media.export.m4a.model.M4aExportPlanningResult
import com.example.bili2media.export.m4a.model.M4aUnsupportedReason
import com.example.bili2media.export.model.MediaTrackSelection
import com.example.bili2media.media.model.MediaTrackInfo
import com.example.bili2media.media.model.MediaTrackKind
import com.example.bili2media.media.model.ProbedMediaFile
import com.example.bili2media.media.probe.MediaProbeFailure
import com.example.bili2media.media.probe.MediaProbeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M4aExportPlannerTest {
    private val planner = M4aExportPlanner()

    @Test
    fun plan_copiesExistingAudioOnlyM4a() {
        val source = success("episode.m4a", size = 4_096L, tracks = arrayOf(aac()))

        assertEquals(
            M4aExportPlanningResult.Ready(
                M4aExportPlan.CopyExistingM4a(
                    source.media.file.input,
                    4_096L,
                    DURATION_US
                )
            ),
            planner.plan(listOf(source))
        )
    }

    @Test
    fun plan_remuxesAacFromM4sAndCombinedMp4() {
        val m4s = success("audio.m4s", tracks = arrayOf(aac(index = 0)))
        val combined = success(
            "combined.mp4",
            tracks = arrayOf(video(index = 0), aac(index = 1))
        )

        assertEquals(
            M4aExportPlan.RemuxAacTrack(
                MediaTrackSelection(m4s.media.file.input, 0, DURATION_US)
            ),
            (planner.plan(listOf(m4s)) as M4aExportPlanningResult.Ready).plan
        )
        assertEquals(
            M4aExportPlan.RemuxAacTrack(
                MediaTrackSelection(combined.media.file.input, 1, DURATION_US)
            ),
            (planner.plan(listOf(combined)) as M4aExportPlanningResult.Ready).plan
        )
    }

    @Test
    fun plan_rejectsMultipleAudioTracksWithoutGuessing() {
        val source = success(
            "multi.mp4",
            tracks = arrayOf(aac(index = 0), aac(index = 1, bitrate = 128_000))
        )

        assertEquals(
            M4aExportPlanningResult.Unsupported(M4aUnsupportedReason.AMBIGUOUS_TRACKS),
            planner.plan(listOf(source))
        )
    }

    @Test
    fun plan_reportsMissingAndUnsupportedAudio() {
        val videoOnly = success("video.m4s", tracks = arrayOf(video()))
        val opus = success(
            "audio.m4s",
            tracks = arrayOf(audio(mimeType = "audio/opus"))
        )

        assertEquals(
            M4aExportPlanningResult.Unsupported(M4aUnsupportedReason.MISSING_AUDIO),
            planner.plan(listOf(videoOnly))
        )
        assertEquals(
            M4aExportPlanningResult.Unsupported(M4aUnsupportedReason.UNSUPPORTED_CODEC),
            planner.plan(listOf(opus))
        )
    }

    @Test
    fun plan_ranksBitrateThenSampleRateThenChannels() {
        val lowBitrate = success(
            "low.m4s",
            tracks = arrayOf(aac(bitrate = 128_000, sampleRate = 96_000, channels = 6))
        )
        val highRateMono = success(
            "high-rate-mono.m4s",
            tracks = arrayOf(aac(bitrate = 192_000, sampleRate = 48_000, channels = 1))
        )
        val highRateStereo = success(
            "high-rate-stereo.m4s",
            tracks = arrayOf(aac(bitrate = 192_000, sampleRate = 48_000, channels = 2))
        )

        val plan = (planner.plan(
            listOf(lowBitrate, highRateMono, highRateStereo)
        ) as M4aExportPlanningResult.Ready).plan as M4aExportPlan.RemuxAacTrack

        assertEquals(highRateStereo.media.file.input, plan.selection.input)
    }

    @Test
    fun plan_prefersM4aCopyThenStablePathForExactQualityTie() {
        val remux = success("a.m4s", tracks = arrayOf(aac()))
        val laterCopy = success("z.m4a", tracks = arrayOf(aac()))
        val earlierCopy = success("b.m4a", tracks = arrayOf(aac()))

        val plan = (planner.plan(
            listOf(remux, laterCopy, earlierCopy)
        ) as M4aExportPlanningResult.Ready).plan as M4aExportPlan.CopyExistingM4a

        assertEquals(earlierCopy.media.file.input, plan.input)
    }

    @Test
    fun plan_ignoresFailedProbeWhenAnotherCandidateIsValid() {
        val failed = MediaProbeResult.Failure(
            mediaFile("broken.m4s"),
            MediaProbeFailure.MALFORMED_MEDIA
        )
        val valid = success("valid.m4a", tracks = arrayOf(aac()))

        assertTrue(planner.plan(listOf(failed, valid)) is M4aExportPlanningResult.Ready)
    }

    @Test
    fun plan_reportsProbeFailureAndNoMedia() {
        val failed = MediaProbeResult.Failure(
            mediaFile("broken.m4s"),
            MediaProbeFailure.MALFORMED_MEDIA
        )

        assertEquals(
            M4aExportPlanningResult.Unsupported(M4aUnsupportedReason.PROBE_FAILED),
            planner.plan(listOf(failed))
        )
        assertEquals(
            M4aExportPlanningResult.Unsupported(M4aUnsupportedReason.NO_MEDIA),
            planner.plan(emptyList())
        )
    }

    private fun success(
        path: String,
        size: Long = 1_024L,
        vararg tracks: MediaTrackInfo
    ): MediaProbeResult.Success {
        return MediaProbeResult.Success(
            ProbedMediaFile(mediaFile(path, size), tracks.toList())
        )
    }

    private fun mediaFile(path: String, size: Long = 1_024L): CacheMediaFile {
        return CacheMediaFile(
            name = path.substringAfterLast('/'),
            relativePath = path,
            size = size,
            input = MediaInputRef.FilePath("/cache/$path")
        )
    }

    private fun aac(
        index: Int = 0,
        bitrate: Int = 192_000,
        sampleRate: Int = 48_000,
        channels: Int = 2
    ): MediaTrackInfo {
        return audio(index, "audio/mp4a-latm", bitrate, sampleRate, channels)
    }

    private fun audio(
        index: Int = 0,
        mimeType: String,
        bitrate: Int = 192_000,
        sampleRate: Int = 48_000,
        channels: Int = 2
    ): MediaTrackInfo {
        return MediaTrackInfo(
            index = index,
            kind = MediaTrackKind.AUDIO,
            mimeType = mimeType,
            durationUs = DURATION_US,
            width = null,
            height = null,
            bitrate = bitrate,
            sampleRate = sampleRate,
            channelCount = channels
        )
    }

    private fun video(index: Int = 0): MediaTrackInfo {
        return MediaTrackInfo(
            index = index,
            kind = MediaTrackKind.VIDEO,
            mimeType = "video/avc",
            durationUs = DURATION_US,
            width = 1920,
            height = 1080,
            bitrate = 4_000_000
        )
    }

    private companion object {
        const val DURATION_US = 10_000_000L
    }
}
