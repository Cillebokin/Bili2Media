package com.example.bili2media.export.mp3.engine

import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.bili2media.AppSettings
import com.example.bili2media.cache.model.MediaInputRef
import com.example.bili2media.export.mp3.model.Mp3ExportPlan
import com.example.bili2media.export.model.MediaTrackSelection
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

class AndroidMp3ExportEngine(
    context: Context,
    private val bitrateKbps: Int = AppSettings.DEFAULT_MP3_BITRATE_KBPS
) : Mp3ExportEngine {
    private val contentResolver: ContentResolver = context.applicationContext.contentResolver

    override fun export(
        plan: Mp3ExportPlan,
        outputUri: String,
        listener: Mp3EngineListener
    ): Mp3EngineResult {
        if (listener.isCancelled()) return Mp3EngineResult.Cancelled

        val session = openExtractorOrNull(plan.selection.input)
            ?: return Mp3EngineResult.Failure(Mp3EngineError.INPUT_OPEN_FAILED)
        var decoder: MediaCodec? = null
        var decoderStarted = false
        var encoder: LameMp3Encoder? = null
        var output: OutputStream? = null
        var result: Mp3EngineResult = Mp3EngineResult.Failure(Mp3EngineError.DECODER_FAILED)
        var cleanupError: Mp3EngineError? = null

        try {
            val extractor = session.extractor
            val selection = plan.selection
            if (selection.trackIndex !in 0 until extractor.trackCount) {
                throw Mp3ExportException(Mp3EngineError.INVALID_TRACK)
            }
            val inputFormat = extractor.getTrackFormat(selection.trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?.takeIf { it.equals(AAC_MIME, ignoreCase = true) }
                ?: throw Mp3ExportException(Mp3EngineError.INVALID_TRACK)
            inputFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)

            val destination = contentResolver.openOutputStream(Uri.parse(outputUri), "w")
                ?: throw Mp3ExportException(Mp3EngineError.OUTPUT_OPEN_FAILED)
            output = destination
            val codec = try {
                MediaCodec.createDecoderByType(mime)
            } catch (_: Exception) {
                throw Mp3ExportException(Mp3EngineError.DECODER_FAILED)
            }
            decoder = codec
            try {
                codec.configure(inputFormat, null, null, 0)
                codec.start()
                decoderStarted = true
            } catch (_: Exception) {
                throw Mp3ExportException(Mp3EngineError.DECODER_FAILED)
            }
            extractor.selectTrack(selection.trackIndex)

            var inputEnded = false
            var outputEnded = false
            val bufferInfo = MediaCodec.BufferInfo()
            val progress = Mp3Progress(selection.durationUs, listener)

            while (!outputEnded) {
                if (listener.isCancelled()) {
                    result = Mp3EngineResult.Cancelled
                    break
                }

                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                            ?: throw Mp3ExportException(Mp3EngineError.DECODER_FAILED)
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                extractor.sampleTime.coerceAtLeast(0L),
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputEnded = true
                        } else {
                            if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED != 0) {
                                throw Mp3ExportException(Mp3EngineError.INVALID_TRACK)
                            }
                            val presentationTimeUs = extractor.sampleTime.coerceAtLeast(0L)
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                presentationTimeUs,
                                0
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        if (encoder == null) {
                            encoder = createEncoder(outputFormat, inputFormat)
                        }
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                    else -> if (outputIndex >= 0) {
                        if (bufferInfo.size > 0 &&
                            bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                        ) {
                            if (encoder == null) {
                                encoder = createEncoder(codec.outputFormat, inputFormat)
                            }
                            val pcm = codec.getOutputBuffer(outputIndex)
                                ?: throw Mp3ExportException(Mp3EngineError.DECODER_FAILED)
                            val samples = decodePcm(pcm, bufferInfo, codec.outputFormat, encoder.channels)
                            try {
                                encoder.encode(samples, destination)
                            } catch (_: Exception) {
                                throw Mp3ExportException(Mp3EngineError.OUTPUT_WRITE_FAILED)
                            }
                            progress.onTime(bufferInfo.presentationTimeUs)
                        }

                        outputEnded = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (outputEnded) {
                            val activeEncoder = encoder
                                ?: throw Mp3ExportException(Mp3EngineError.UNSUPPORTED_AUDIO_FORMAT)
                            try {
                                activeEncoder.finish(destination)
                                destination.flush()
                            } catch (_: Exception) {
                                throw Mp3ExportException(Mp3EngineError.OUTPUT_WRITE_FAILED)
                            }
                            result = Mp3EngineResult.Success
                        }
                    }
                }
            }
        } catch (error: Mp3ExportException) {
            result = Mp3EngineResult.Failure(error.error)
        } catch (_: FileNotFoundException) {
            result = Mp3EngineResult.Failure(Mp3EngineError.INPUT_OPEN_FAILED)
        } catch (_: SecurityException) {
            result = Mp3EngineResult.Failure(Mp3EngineError.INPUT_OPEN_FAILED)
        } catch (_: Exception) {
            result = Mp3EngineResult.Failure(Mp3EngineError.DECODER_FAILED)
        } finally {
            try {
                output?.close()
            } catch (_: Exception) {
                cleanupError = Mp3EngineError.OUTPUT_WRITE_FAILED
            }
            try {
                encoder?.close()
            } catch (_: Exception) {
                cleanupError = cleanupError ?: Mp3EngineError.ENCODER_FAILED
            }
            if (decoderStarted) {
                runCatching { decoder?.stop() }
                    .onFailure { cleanupError = cleanupError ?: Mp3EngineError.DECODER_FAILED }
            }
            runCatching { decoder?.release() }
                .onFailure { cleanupError = cleanupError ?: Mp3EngineError.DECODER_FAILED }
            try {
                session.close()
            } catch (_: Exception) {
                cleanupError = cleanupError ?: Mp3EngineError.INPUT_OPEN_FAILED
            }
            if (result == Mp3EngineResult.Success && cleanupError != null) {
                result = Mp3EngineResult.Failure(cleanupError!!)
            }
        }

        if (result == Mp3EngineResult.Success) listener.onProgress(100)
        return result
    }

    private fun createEncoder(outputFormat: MediaFormat, inputFormat: MediaFormat): LameMp3Encoder {
        val sampleRate = outputFormat.intValueOrNull(MediaFormat.KEY_SAMPLE_RATE)
            ?: inputFormat.intValueOrNull(MediaFormat.KEY_SAMPLE_RATE)
            ?: throw Mp3ExportException(Mp3EngineError.UNSUPPORTED_AUDIO_FORMAT)
        val channels = outputFormat.intValueOrNull(MediaFormat.KEY_CHANNEL_COUNT)
            ?: inputFormat.intValueOrNull(MediaFormat.KEY_CHANNEL_COUNT)
            ?: throw Mp3ExportException(Mp3EngineError.UNSUPPORTED_AUDIO_FORMAT)
        if (channels !in 1..2 || sampleRate !in 8_000..48_000) {
            throw Mp3ExportException(Mp3EngineError.UNSUPPORTED_AUDIO_FORMAT)
        }
        return try {
            LameMp3Encoder(sampleRate, channels, bitrateKbps)
        } catch (_: Exception) {
            throw Mp3ExportException(Mp3EngineError.ENCODER_FAILED)
        }
    }

    private fun decodePcm(
        source: ByteBuffer,
        info: MediaCodec.BufferInfo,
        format: MediaFormat,
        channels: Int
    ): ShortArray {
        val encoding = format.intValueOrNull(MediaFormat.KEY_PCM_ENCODING)
            ?: AudioFormat.ENCODING_PCM_16BIT
        val bytesPerSample = when (encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> throw Mp3ExportException(Mp3EngineError.UNSUPPORTED_AUDIO_FORMAT)
        }
        if (info.offset < 0 || info.size < 0 || info.size % (bytesPerSample * channels) != 0) {
            throw Mp3ExportException(Mp3EngineError.UNSUPPORTED_AUDIO_FORMAT)
        }
        val input = source.duplicate().order(ByteOrder.nativeOrder()).apply {
            position(info.offset)
            limit(info.offset + info.size)
        }
        val samples = ShortArray(info.size / bytesPerSample)
        if (encoding == AudioFormat.ENCODING_PCM_16BIT) {
            for (index in samples.indices) samples[index] = input.short
        } else {
            for (index in samples.indices) {
                val value = input.float.coerceIn(-1f, 1f)
                samples[index] = (value * Short.MAX_VALUE).roundToInt().toShort()
            }
        }
        return samples
    }

    private fun MediaFormat.intValueOrNull(key: String): Int? =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

    private fun openExtractorOrNull(input: MediaInputRef): ExtractorSession? =
        runCatching { ExtractorSession.open(contentResolver, input) }.getOrNull()

    private class ExtractorSession private constructor(
        val extractor: MediaExtractor,
        private val descriptor: AssetFileDescriptor?
    ) : Closeable {
        override fun close() {
            var failure: Throwable? = null
            runCatching { extractor.release() }.onFailure { failure = it }
            runCatching { descriptor?.close() }.onFailure { error ->
                failure?.addSuppressed(error) ?: run { failure = error }
            }
            failure?.let { throw it }
        }

        companion object {
            fun open(contentResolver: ContentResolver, input: MediaInputRef): ExtractorSession {
                val extractor = MediaExtractor()
                var descriptor: AssetFileDescriptor? = null
                try {
                    when (input) {
                        is MediaInputRef.FilePath -> {
                            val file = File(input.path)
                            if (!file.isFile || !file.canRead()) throw FileNotFoundException(input.path)
                            extractor.setDataSource(file.absolutePath)
                        }

                        is MediaInputRef.ContentUri -> {
                            descriptor = contentResolver.openAssetFileDescriptor(Uri.parse(input.uri), "r")
                                ?: throw FileNotFoundException(input.uri)
                            if (descriptor.declaredLength >= 0L) {
                                extractor.setDataSource(
                                    descriptor.fileDescriptor,
                                    descriptor.startOffset,
                                    descriptor.declaredLength
                                )
                            } else {
                                extractor.setDataSource(descriptor.fileDescriptor)
                            }
                        }
                    }
                    return ExtractorSession(extractor, descriptor)
                } catch (error: Throwable) {
                    runCatching { extractor.release() }
                    runCatching { descriptor?.close() }
                    throw error
                }
            }
        }
    }

    private class Mp3Progress(
        durationUs: Long,
        private val listener: Mp3EngineListener
    ) {
        private val durationUs = durationUs.coerceAtLeast(0L)
        private var lastPercent = -1

        fun onTime(presentationTimeUs: Long) {
            if (durationUs <= 0L) return
            val percent = ((presentationTimeUs.coerceAtMost(durationUs).coerceAtLeast(0L) * 100L) /
                durationUs).toInt().coerceIn(0, 99)
            if (percent != lastPercent) {
                listener.onProgress(percent)
                lastPercent = percent
            }
        }
    }

    private class Mp3ExportException(val error: Mp3EngineError) : Exception()

    private companion object {
        const val AAC_MIME = "audio/mp4a-latm"
        const val CODEC_TIMEOUT_US = 10_000L
    }
}
