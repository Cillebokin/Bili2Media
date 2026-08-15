package com.example.bili2media.testmedia

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

class TestMediaFixtureFactory(
    private val context: Context
) {
    fun createVideoOnlyM4s(): File {
        return createFixture(".m4s", ::encodeVideo)
    }

    fun createAudioOnlyM4s(): File {
        return createFixture(".m4s", ::encodeAudio)
    }

    fun createCombinedMp4(): File {
        val video = createVideoOnlyM4s()
        val audio = createAudioOnlyM4s()
        val output = newOutputFile(".mp4")
        return try {
            muxSources(listOf(video, audio), output)
            output
        } catch (error: Throwable) {
            output.delete()
            throw error
        } finally {
            video.delete()
            audio.delete()
        }
    }

    private fun createFixture(
        suffix: String,
        encoder: (File) -> Unit
    ): File {
        val output = newOutputFile(suffix)
        return try {
            encoder(output)
            output
        } catch (error: Throwable) {
            output.delete()
            throw error
        }
    }

    private fun encodeVideo(output: File) {
        val format = MediaFormat.createVideoFormat(VIDEO_MIME, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, VIDEO_FRAME_SIZE)
        }
        val frame = ByteArray(VIDEO_FRAME_SIZE) { index ->
            if (index < VIDEO_WIDTH * VIDEO_HEIGHT) 32 else 128.toByte()
        }
        encodeSingleTrack(output, VIDEO_MIME, format) { session ->
            repeat(VIDEO_FRAME_COUNT) { frameIndex ->
                session.queue(
                    bytes = frame,
                    presentationTimeUs = frameIndex * 1_000_000L / VIDEO_FRAME_RATE,
                    flags = 0
                )
            }
            session.queue(
                bytes = EMPTY_BYTES,
                presentationTimeUs = VIDEO_FRAME_COUNT * 1_000_000L / VIDEO_FRAME_RATE,
                flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
        }
    }

    private fun encodeAudio(output: File) {
        val format = MediaFormat.createAudioFormat(
            AUDIO_MIME,
            AUDIO_SAMPLE_RATE,
            AUDIO_CHANNEL_COUNT
        ).apply {
            setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AUDIO_FRAME_SIZE)
        }
        val silentPcm = ByteArray(AUDIO_FRAME_SIZE)
        encodeSingleTrack(output, AUDIO_MIME, format) { session ->
            repeat(AUDIO_FRAME_COUNT) { frameIndex ->
                session.queue(
                    bytes = silentPcm,
                    presentationTimeUs = frameIndex * AUDIO_SAMPLES_PER_FRAME * 1_000_000L /
                        AUDIO_SAMPLE_RATE,
                    flags = 0
                )
            }
            session.queue(
                bytes = EMPTY_BYTES,
                presentationTimeUs = AUDIO_FRAME_COUNT * AUDIO_SAMPLES_PER_FRAME * 1_000_000L /
                    AUDIO_SAMPLE_RATE,
                flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
        }
    }

    private fun encodeSingleTrack(
        output: File,
        mimeType: String,
        format: MediaFormat,
        feedInput: (EncoderSession) -> Unit
    ) {
        val codec = createFixtureEncoder(mimeType)
        val muxer = MediaMuxer(
            output.absolutePath,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )
        val session = EncoderSession(codec, muxer)
        var codecStarted = false
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            codecStarted = true
            feedInput(session)
            session.drainToEndOfStream()
        } finally {
            if (codecStarted) {
                runCatching { codec.stop() }
            }
            codec.release()
            if (session.muxerStarted) {
                runCatching { muxer.stop() }
            }
            muxer.release()
        }
    }

    private fun muxSources(sources: List<File>, output: File) {
        val muxer = MediaMuxer(
            output.absolutePath,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )
        val tracks = mutableListOf<SourceTrack>()
        var muxerStarted = false
        try {
            sources.forEach { source ->
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(source.absolutePath)
                    require(extractor.trackCount == 1) {
                        "Expected one track in ${source.name}, found ${extractor.trackCount}"
                    }
                    tracks += SourceTrack(
                        extractor = extractor,
                        sourceTrackIndex = 0,
                        outputTrackIndex = muxer.addTrack(extractor.getTrackFormat(0))
                    )
                } catch (error: Throwable) {
                    extractor.release()
                    throw error
                }
            }
            muxer.start()
            muxerStarted = true
            tracks.forEach { track -> copyTrack(track, muxer) }
        } finally {
            tracks.forEach { it.extractor.release() }
            if (muxerStarted) {
                runCatching { muxer.stop() }
            }
            muxer.release()
        }
    }

    private fun copyTrack(track: SourceTrack, muxer: MediaMuxer) {
        val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
        val info = MediaCodec.BufferInfo()
        track.extractor.selectTrack(track.sourceTrackIndex)
        while (true) {
            buffer.clear()
            val size = track.extractor.readSampleData(buffer, 0)
            if (size < 0) {
                break
            }
            info.set(
                0,
                size,
                track.extractor.sampleTime.coerceAtLeast(0L),
                track.extractor.sampleFlags
            )
            muxer.writeSampleData(track.outputTrackIndex, buffer, info)
            if (!track.extractor.advance()) {
                break
            }
        }
    }

    private fun newOutputFile(suffix: String): File {
        return File(
            context.cacheDir,
            "media-fixture-${System.nanoTime()}$suffix"
        ).also { it.delete() }
    }

    private fun createFixtureEncoder(mimeType: String): MediaCodec {
        val codecInfo = MediaCodecList(MediaCodecList.ALL_CODECS)
            .codecInfos
            .asSequence()
            .filter { it.isEncoder }
            .filter { info ->
                info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
            }
            .filter { info ->
                if (mimeType != VIDEO_MIME) {
                    true
                } else {
                    val capabilities = runCatching {
                        info.getCapabilitiesForType(mimeType)
                    }.getOrNull() ?: return@filter false
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible in
                        capabilities.colorFormats &&
                        capabilities.videoCapabilities?.isSizeSupported(
                            VIDEO_WIDTH,
                            VIDEO_HEIGHT
                        ) == true
                }
            }
            .sortedWith(
                compareByDescending<MediaCodecInfo> { it.isSoftwareOnly }
                    .thenBy { it.name }
            )
            .firstOrNull()
            ?: error("No test encoder supports $mimeType")
        return MediaCodec.createByCodecName(codecInfo.name)
    }

    private class EncoderSession(
        private val codec: MediaCodec,
        private val muxer: MediaMuxer
    ) {
        private val bufferInfo = MediaCodec.BufferInfo()
        private var outputTrackIndex = -1
        private var endOfStreamSeen = false

        var muxerStarted: Boolean = false
            private set

        fun queue(bytes: ByteArray, presentationTimeUs: Long, flags: Int) {
            while (true) {
                val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val input = requireNotNull(codec.getInputBuffer(inputIndex))
                    input.clear()
                    require(bytes.size <= input.remaining()) {
                        "Encoder input buffer is too small for ${bytes.size} bytes"
                    }
                    input.put(bytes)
                    codec.queueInputBuffer(
                        inputIndex,
                        0,
                        bytes.size,
                        presentationTimeUs,
                        flags
                    )
                    drainAvailable()
                    return
                }
                drainAvailable()
            }
        }

        fun drainToEndOfStream() {
            if (endOfStreamSeen) {
                return
            }
            var idleCount = 0
            while (idleCount < MAX_DRAIN_IDLE_COUNT) {
                val drained = drainOne(CODEC_TIMEOUT_US)
                if (endOfStreamSeen) {
                    return
                }
                idleCount = if (drained) 0 else idleCount + 1
            }
            error("Timed out waiting for encoder end of stream")
        }

        private fun drainAvailable() {
            while (!endOfStreamSeen && drainOne(0L)) {
                // Drain every currently available buffer before feeding more input.
            }
        }

        private fun drainOne(timeoutUs: Long): Boolean {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return false
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!muxerStarted) { "Encoder output format changed twice" }
                    outputTrackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                    return true
                }
                outputIndex >= 0 -> {
                    val output = requireNotNull(codec.getOutputBuffer(outputIndex))
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0) {
                        check(muxerStarted) { "Encoded sample arrived before output format" }
                        output.position(bufferInfo.offset)
                        output.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(outputTrackIndex, output, bufferInfo)
                    }
                    val endOfStream =
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    endOfStreamSeen = endOfStreamSeen || endOfStream
                    codec.releaseOutputBuffer(outputIndex, false)
                    return true
                }
                else -> return true
            }
        }
    }

    private data class SourceTrack(
        val extractor: MediaExtractor,
        val sourceTrackIndex: Int,
        val outputTrackIndex: Int
    )

    companion object {
        const val VIDEO_MIME = "video/avc"
        const val VIDEO_WIDTH = 160
        const val VIDEO_HEIGHT = 120
        const val VIDEO_FRAME_RATE = 10
        const val VIDEO_FRAME_COUNT = 4
        private const val VIDEO_FRAME_SIZE = VIDEO_WIDTH * VIDEO_HEIGHT * 3 / 2

        private const val AUDIO_MIME = "audio/mp4a-latm"
        private const val AUDIO_SAMPLE_RATE = 44_100
        private const val AUDIO_CHANNEL_COUNT = 1
        private const val AUDIO_SAMPLES_PER_FRAME = 1_024
        private const val AUDIO_FRAME_COUNT = 8
        private const val AUDIO_FRAME_SIZE = AUDIO_SAMPLES_PER_FRAME * 2

        private const val CODEC_TIMEOUT_US = 10_000L
        private const val MAX_DRAIN_IDLE_COUNT = 500
        private val EMPTY_BYTES = ByteArray(0)
    }
}
