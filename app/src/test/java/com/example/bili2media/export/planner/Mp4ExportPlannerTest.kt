package com.example.bili2media.export.planner

import com.example.bili2media.cache.model.CacheMediaFile
import com.example.bili2media.cache.model.MediaInputRef
import com.example.bili2media.export.model.MediaTrackSelection
import com.example.bili2media.export.model.Mp4ExportPlan
import com.example.bili2media.export.model.Mp4ExportPlanningResult
import com.example.bili2media.export.model.Mp4UnsupportedReason
import com.example.bili2media.media.model.MediaTrackInfo
import com.example.bili2media.media.model.MediaTrackKind
import com.example.bili2media.media.model.ProbedMediaFile
import com.example.bili2media.media.probe.MediaProbeFailure
import com.example.bili2media.media.probe.MediaProbeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Mp4ExportPlannerTest {
    private val planner = Mp4ExportPlanner()

    @Test
    fun plan_copiesExistingMp4WithOneSupportedVideoAndAudioTrack() {
        val combined = success(
            path = "80/episode.mp4",
            size = 4_096L,
            track(0, "video/avc", width = 1920, height = 1080, bitrate = 4_000_000),
            track(1, "audio/mp4a-latm")
        )

        val result = planner.plan(listOf(combined))

        assertEquals(
            Mp4ExportPlanningResult.Ready(
                Mp4ExportPlan.CopyExistingMp4(
                    input = combined.media.file.input,
                    sourceBytes = 4_096L,
                    durationUs = DURATION_US
                )
            ),
            result
        )
    }

    @Test
    fun plan_muxesOneVideoAndOneAudioM4sFromSameParent() {
        val video = success(
            "80/video.m4s",
            tracks = arrayOf(
                track(0, "video/hevc", width = 1920, height = 1080, bitrate = 3_000_000)
            )
        )
        val audio = success(
            "80/audio.m4s",
            tracks = arrayOf(track(0, "audio/mp4a-latm"))
        )

        val result = planner.plan(listOf(audio, video))

        assertEquals(
            Mp4ExportPlanningResult.Ready(
                Mp4ExportPlan.MuxM4s(
                    video = MediaTrackSelection(video.media.file.input, 0, DURATION_US),
                    audio = MediaTrackSelection(audio.media.file.input, 0, DURATION_US),
                    durationUs = DURATION_US
                )
            ),
            result
        )
    }

    @Test
    fun plan_neverPairsM4sFilesFromDifferentParents() {
        val video = success(
            "80/video.m4s",
            tracks = arrayOf(track(0, "video/avc", width = 1920, height = 1080))
        )
        val audio = success(
            "64/audio.m4s",
            tracks = arrayOf(track(0, "audio/mp4a-latm"))
        )

        val result = planner.plan(listOf(video, audio))

        assertTrue(result is Mp4ExportPlanningResult.Unsupported)
    }

    @Test
    fun plan_reportsMissingAudioOrVideo() {
        val videoOnly = success(
            "80/video.m4s",
            tracks = arrayOf(track(0, "video/avc", width = 1920, height = 1080))
        )
        val audioOnly = success(
            "80/audio.m4s",
            tracks = arrayOf(track(0, "audio/mp4a-latm"))
        )

        assertEquals(
            Mp4ExportPlanningResult.Unsupported(Mp4UnsupportedReason.MISSING_AUDIO),
            planner.plan(listOf(videoOnly))
        )
        assertEquals(
            Mp4ExportPlanningResult.Unsupported(Mp4UnsupportedReason.MISSING_VIDEO),
            planner.plan(listOf(audioOnly))
        )
    }

    @Test
    fun plan_rejectsMultipleTracksOrFilesWithoutGuessing() {
        val multiTrackMp4 = success(
            "episode.mp4",
            tracks = arrayOf(
                track(0, "video/avc", width = 1920, height = 1080),
                track(1, "video/hevc", width = 1280, height = 720),
                track(2, "audio/mp4a-latm")
            )
        )
        val firstVideo = success(
            "80/video-1.m4s",
            tracks = arrayOf(track(0, "video/avc", width = 1920, height = 1080))
        )
        val secondVideo = success(
            "80/video-2.m4s",
            tracks = arrayOf(track(0, "video/avc", width = 1280, height = 720))
        )
        val audio = success(
            "80/audio.m4s",
            tracks = arrayOf(track(0, "audio/mp4a-latm"))
        )

        assertEquals(
            Mp4ExportPlanningResult.Unsupported(Mp4UnsupportedReason.AMBIGUOUS_TRACKS),
            planner.plan(listOf(multiTrackMp4))
        )
        assertEquals(
            Mp4ExportPlanningResult.Unsupported(Mp4UnsupportedReason.AMBIGUOUS_TRACKS),
            planner.plan(listOf(firstVideo, secondVideo, audio))
        )
    }

    @Test
    fun plan_acceptsOnlyNativeMuxerCodecPolicy() {
        listOf("video/avc", "video/hevc").forEachIndexed { index, mimeType ->
            val video = success(
                "supported-$index/video.m4s",
                tracks = arrayOf(track(0, mimeType, width = 1280, height = 720))
            )
            val audio = success(
                "supported-$index/audio.m4s",
                tracks = arrayOf(track(0, "audio/mp4a-latm"))
            )
            assertTrue(
                "$mimeType should be supported",
                planner.plan(listOf(video, audio)) is Mp4ExportPlanningResult.Ready
            )
        }

        val vp9 = success(
            "unsupported/video.m4s",
            tracks = arrayOf(track(0, "video/x-vnd.on2.vp9", width = 1280, height = 720))
        )
        val opus = success(
            "unsupported/audio.m4s",
            tracks = arrayOf(track(0, "audio/opus"))
        )

        assertEquals(
            Mp4ExportPlanningResult.Unsupported(Mp4UnsupportedReason.UNSUPPORTED_CODEC),
            planner.plan(listOf(vp9, opus))
        )
    }

    @Test
    fun plan_choosesHighestPixelCountThenVideoBitrate() {
        val low = combinedMp4(
            path = "low.mp4",
            width = 1280,
            height = 720,
            bitrate = 8_000_000
        )
        val highLowBitrate = combinedMp4(
            path = "high-low-bitrate.mp4",
            width = 1920,
            height = 1080,
            bitrate = 3_000_000
        )
        val highHighBitrate = combinedMp4(
            path = "high-high-bitrate.mp4",
            width = 1920,
            height = 1080,
            bitrate = 5_000_000
        )

        val result = planner.plan(listOf(low, highLowBitrate, highHighBitrate))

        assertEquals(
            highHighBitrate.media.file.input,
            ((result as Mp4ExportPlanningResult.Ready).plan as
                Mp4ExportPlan.CopyExistingMp4).input
        )
    }

    @Test
    fun plan_prefersMp4CopyThenStablePathWhenQualityIsExactlyEqual() {
        val video = success(
            "z/video.m4s",
            tracks = arrayOf(
                track(0, "video/avc", width = 1920, height = 1080, bitrate = 4_000_000)
            )
        )
        val audio = success(
            "z/audio.m4s",
            tracks = arrayOf(track(0, "audio/mp4a-latm"))
        )
        val laterMp4 = combinedMp4("z-copy.mp4", 1920, 1080, 4_000_000)
        val earlierMp4 = combinedMp4("a-copy.mp4", 1920, 1080, 4_000_000)

        val result = planner.plan(listOf(video, audio, laterMp4, earlierMp4))

        assertEquals(
            earlierMp4.media.file.input,
            ((result as Mp4ExportPlanningResult.Ready).plan as
                Mp4ExportPlan.CopyExistingMp4).input
        )
    }

    @Test
    fun plan_ignoresFailedProbeWhenAnotherCandidateIsValid() {
        val failed = MediaProbeResult.Failure(
            file = mediaFile("broken.m4s"),
            reason = MediaProbeFailure.MALFORMED_MEDIA
        )
        val valid = combinedMp4("valid.mp4", 1280, 720, 2_000_000)

        val result = planner.plan(listOf(failed, valid))

        assertTrue(result is Mp4ExportPlanningResult.Ready)
    }

    @Test
    fun plan_reportsProbeFailureWhenEveryProbeFailed() {
        val results = listOf(
            MediaProbeResult.Failure(
                mediaFile("broken-video.m4s"),
                MediaProbeFailure.MALFORMED_MEDIA
            ),
            MediaProbeResult.Failure(
                mediaFile("missing-audio.m4s"),
                MediaProbeFailure.INPUT_UNAVAILABLE
            )
        )

        assertEquals(
            Mp4ExportPlanningResult.Unsupported(Mp4UnsupportedReason.PROBE_FAILED),
            planner.plan(results)
        )
    }

    @Test
    fun plan_reportsNoMediaForEmptyInput() {
        assertEquals(
            Mp4ExportPlanningResult.Unsupported(Mp4UnsupportedReason.NO_MEDIA),
            planner.plan(emptyList())
        )
    }

    private fun combinedMp4(
        path: String,
        width: Int,
        height: Int,
        bitrate: Int
    ): MediaProbeResult.Success {
        return success(
            path,
            tracks = arrayOf(
                track(0, "video/avc", width = width, height = height, bitrate = bitrate),
                track(1, "audio/mp4a-latm")
            )
        )
    }

    private fun success(
        path: String,
        size: Long = 1_024L,
        vararg tracks: MediaTrackInfo
    ): MediaProbeResult.Success {
        return MediaProbeResult.Success(
            ProbedMediaFile(
                file = mediaFile(path, size),
                tracks = tracks.toList()
            )
        )
    }

    private fun track(
        index: Int,
        mimeType: String,
        durationUs: Long = DURATION_US,
        width: Int? = null,
        height: Int? = null,
        bitrate: Int? = null
    ): MediaTrackInfo {
        return MediaTrackInfo(
            index = index,
            kind = MediaTrackKind.fromMime(mimeType),
            mimeType = mimeType,
            durationUs = durationUs,
            width = width,
            height = height,
            bitrate = bitrate
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

    private companion object {
        const val DURATION_US = 10_000_000L
    }
}
