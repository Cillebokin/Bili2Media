package com.example.bili2media.export.engine

import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.bili2media.cache.model.MediaInputRef
import com.example.bili2media.export.model.MediaTrackSelection
import com.example.bili2media.export.model.Mp4ExportPlan
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.ByteBuffer

class AndroidMp4ExportEngine(
    context: Context
) : Mp4ExportEngine {
    private val contentResolver: ContentResolver = context.applicationContext.contentResolver

    override fun export(
        plan: Mp4ExportPlan,
        outputUri: String,
        listener: Mp4EngineListener
    ): Mp4EngineResult {
        return when (plan) {
            is Mp4ExportPlan.CopyExistingMp4 -> copy(plan, outputUri, listener)
            is Mp4ExportPlan.MuxM4s -> mux(plan, outputUri, listener)
        }
    }

    private fun copy(
        plan: Mp4ExportPlan.CopyExistingMp4,
        outputUri: String,
        listener: Mp4EngineListener
    ): Mp4EngineResult {
        if (listener.isCancelled()) {
            return Mp4EngineResult.Cancelled
        }
        val input = try {
            openInputStream(plan.input)
        } catch (_: Exception) {
            return Mp4EngineResult.Failure(Mp4EngineError.INPUT_OPEN_FAILED)
        }
        return input.use { source ->
            val output = try {
                contentResolver.openOutputStream(Uri.parse(outputUri), "w")
                    ?: return@use Mp4EngineResult.Failure(Mp4EngineError.OUTPUT_OPEN_FAILED)
            } catch (_: Exception) {
                return@use Mp4EngineResult.Failure(Mp4EngineError.OUTPUT_OPEN_FAILED)
            }
            output.use { destination ->
                try {
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    var copiedBytes = 0L
                    var lastProgress = -1
                    while (true) {
                        if (listener.isCancelled()) {
                            return@use Mp4EngineResult.Cancelled
                        }
                        val read = source.read(buffer)
                        if (read < 0) {
                            break
                        }
                        destination.write(buffer, 0, read)
                        copiedBytes += read
                        if (plan.sourceBytes > 0L) {
                            val progress = ((copiedBytes * 100L) / plan.sourceBytes)
                                .toInt()
                                .coerceIn(0, 99)
                            if (progress != lastProgress) {
                                listener.onProgress(progress)
                                lastProgress = progress
                            }
                        }
                    }
                    destination.flush()
                    if (listener.isCancelled()) {
                        Mp4EngineResult.Cancelled
                    } else {
                        listener.onProgress(100)
                        Mp4EngineResult.Success
                    }
                } catch (_: Exception) {
                    Mp4EngineResult.Failure(Mp4EngineError.COPY_FAILED)
                }
            }
        }
    }

    private fun mux(
        plan: Mp4ExportPlan.MuxM4s,
        outputUri: String,
        listener: Mp4EngineListener
    ): Mp4EngineResult {
        if (listener.isCancelled()) {
            return Mp4EngineResult.Cancelled
        }
        val videoSession = openExtractorOrNull(plan.video.input)
            ?: return Mp4EngineResult.Failure(Mp4EngineError.INPUT_OPEN_FAILED)
        try {
            val audioSession = openExtractorOrNull(plan.audio.input)
                ?: return Mp4EngineResult.Failure(Mp4EngineError.INPUT_OPEN_FAILED)
            try {
                val videoFormat = videoSession.trackFormatOrNull(plan.video, VIDEO_PREFIX)
                    ?: return Mp4EngineResult.Failure(Mp4EngineError.INVALID_TRACK)
                val audioFormat = audioSession.trackFormatOrNull(plan.audio, AUDIO_PREFIX)
                    ?: return Mp4EngineResult.Failure(Mp4EngineError.INVALID_TRACK)
                val outputDescriptor = openOutputDescriptorOrNull(outputUri)
                    ?: return Mp4EngineResult.Failure(Mp4EngineError.OUTPUT_OPEN_FAILED)
                outputDescriptor.use { descriptor ->
                    val muxer = try {
                        MediaMuxer(
                            descriptor.fileDescriptor,
                            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                        )
                    } catch (_: Exception) {
                        return Mp4EngineResult.Failure(Mp4EngineError.OUTPUT_OPEN_FAILED)
                    }
                    return runMux(
                        muxer = muxer,
                        videoSession = videoSession,
                        audioSession = audioSession,
                        videoSelection = plan.video,
                        audioSelection = plan.audio,
                        videoFormat = videoFormat,
                        audioFormat = audioFormat,
                        listener = listener
                    )
                }
            } finally {
                audioSession.close()
            }
        } finally {
            videoSession.close()
        }
    }

    private fun runMux(
        muxer: MediaMuxer,
        videoSession: ExtractorSession,
        audioSession: ExtractorSession,
        videoSelection: MediaTrackSelection,
        audioSelection: MediaTrackSelection,
        videoFormat: MediaFormat,
        audioFormat: MediaFormat,
        listener: Mp4EngineListener
    ): Mp4EngineResult {
        var muxerStarted = false
        var result: Mp4EngineResult = Mp4EngineResult.Failure(Mp4EngineError.MUXER_FAILED)
        try {
            val outputVideoTrack = muxer.addTrack(videoFormat)
            val outputAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()
            muxerStarted = true
            val progress = MuxProgress(
                videoDurationUs = videoSelection.durationUs,
                audioDurationUs = audioSelection.durationUs,
                listener = listener
            )

            val videoCopied = copyTrack(
                session = videoSession,
                selection = videoSelection,
                muxer = muxer,
                outputTrackIndex = outputVideoTrack,
                listener = listener,
                onPresentationTime = progress::onVideoProgress
            )
            if (!videoCopied) {
                result = Mp4EngineResult.Cancelled
            } else {
                progress.completeVideo()
                val audioCopied = copyTrack(
                    session = audioSession,
                    selection = audioSelection,
                    muxer = muxer,
                    outputTrackIndex = outputAudioTrack,
                    listener = listener,
                    onPresentationTime = progress::onAudioProgress
                )
                result = if (!audioCopied || listener.isCancelled()) {
                    Mp4EngineResult.Cancelled
                } else {
                    progress.completeAudio()
                    listener.onProgress(100)
                    Mp4EngineResult.Success
                }
            }
        } catch (_: IllegalArgumentException) {
            result = Mp4EngineResult.Failure(
                if (muxerStarted) Mp4EngineError.MUXER_FAILED else Mp4EngineError.INVALID_TRACK
            )
        } catch (_: Exception) {
            result = Mp4EngineResult.Failure(Mp4EngineError.MUXER_FAILED)
        } finally {
            var closeFailed = false
            if (muxerStarted) {
                try {
                    muxer.stop()
                } catch (_: Exception) {
                    closeFailed = true
                }
            }
            runCatching { muxer.release() }
                .onFailure { closeFailed = true }
            if (closeFailed && result == Mp4EngineResult.Success) {
                result = Mp4EngineResult.Failure(Mp4EngineError.MUXER_FAILED)
            }
        }
        return result
    }

    private fun copyTrack(
        session: ExtractorSession,
        selection: MediaTrackSelection,
        muxer: MediaMuxer,
        outputTrackIndex: Int,
        listener: Mp4EngineListener,
        onPresentationTime: (Long) -> Unit
    ): Boolean {
        val extractor = session.extractor
        extractor.selectTrack(selection.trackIndex)
        var buffer = ByteBuffer.allocate(INITIAL_SAMPLE_BUFFER_SIZE)
        val bufferInfo = MediaCodec.BufferInfo()
        var firstPresentationTimeUs: Long? = null
        try {
            while (true) {
                if (listener.isCancelled()) {
                    return false
                }
                val declaredSize = extractor.sampleSize
                if (declaredSize > Int.MAX_VALUE) {
                    throw IllegalArgumentException("Media sample is too large")
                }
                if (declaredSize > buffer.capacity()) {
                    buffer = ByteBuffer.allocate(declaredSize.toInt())
                }
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) {
                    break
                }
                val sourceTimeUs = extractor.sampleTime.coerceAtLeast(0L)
                val firstTimeUs = firstPresentationTimeUs ?: sourceTimeUs.also {
                    firstPresentationTimeUs = it
                }
                val normalizedTimeUs = (sourceTimeUs - firstTimeUs).coerceAtLeast(0L)
                bufferInfo.set(
                    0,
                    sampleSize,
                    normalizedTimeUs,
                    extractor.sampleFlags.toMuxerFlags()
                )
                muxer.writeSampleData(outputTrackIndex, buffer, bufferInfo)
                onPresentationTime(normalizedTimeUs)
                if (!extractor.advance()) {
                    break
                }
            }
            return true
        } finally {
            runCatching { extractor.unselectTrack(selection.trackIndex) }
        }
    }

    private fun openInputStream(input: MediaInputRef): InputStream {
        return when (input) {
            is MediaInputRef.FilePath -> {
                val file = File(input.path)
                if (!file.isFile || !file.canRead()) {
                    throw FileNotFoundException(input.path)
                }
                FileInputStream(file)
            }

            is MediaInputRef.ContentUri -> {
                contentResolver.openInputStream(Uri.parse(input.uri))
                    ?: throw FileNotFoundException(input.uri)
            }
        }
    }

    private fun Int.toMuxerFlags(): Int {
        if (this and MediaExtractor.SAMPLE_FLAG_ENCRYPTED != 0) {
            throw IllegalArgumentException("Encrypted media samples are not supported")
        }
        var muxerFlags = 0
        if (this and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
            muxerFlags = muxerFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        if (this and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) {
            muxerFlags = muxerFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
        }
        return muxerFlags
    }

    private fun openExtractorOrNull(input: MediaInputRef): ExtractorSession? {
        return runCatching { ExtractorSession.open(contentResolver, input) }.getOrNull()
    }

    private fun openOutputDescriptorOrNull(outputUri: String): ParcelFileDescriptor? {
        return runCatching {
            contentResolver.openFileDescriptor(Uri.parse(outputUri), "rwt")
        }.getOrNull()
    }

    private fun ExtractorSession.trackFormatOrNull(
        selection: MediaTrackSelection,
        expectedMimePrefix: String
    ): MediaFormat? {
        if (selection.trackIndex !in 0 until extractor.trackCount) {
            return null
        }
        val format = runCatching { extractor.getTrackFormat(selection.trackIndex) }.getOrNull()
            ?: return null
        val mimeType = runCatching { format.getString(MediaFormat.KEY_MIME) }.getOrNull()
            ?: return null
        return format.takeIf { mimeType.startsWith(expectedMimePrefix, ignoreCase = true) }
    }

    private class ExtractorSession private constructor(
        val extractor: MediaExtractor,
        private val descriptor: AssetFileDescriptor?
    ) : Closeable {
        override fun close() {
            runCatching { extractor.release() }
            runCatching { descriptor?.close() }
        }

        companion object {
            fun open(
                contentResolver: ContentResolver,
                input: MediaInputRef
            ): ExtractorSession {
                val extractor = MediaExtractor()
                var descriptor: AssetFileDescriptor? = null
                try {
                    when (input) {
                        is MediaInputRef.FilePath -> {
                            val file = File(input.path)
                            if (!file.isFile || !file.canRead()) {
                                throw FileNotFoundException(input.path)
                            }
                            extractor.setDataSource(file.absolutePath)
                        }

                        is MediaInputRef.ContentUri -> {
                            descriptor = contentResolver.openAssetFileDescriptor(
                                Uri.parse(input.uri),
                                "r"
                            ) ?: throw FileNotFoundException(input.uri)
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

    private class MuxProgress(
        videoDurationUs: Long,
        audioDurationUs: Long,
        private val listener: Mp4EngineListener
    ) {
        private val videoDurationUs = videoDurationUs.coerceAtLeast(0L)
        private val audioDurationUs = audioDurationUs.coerceAtLeast(0L)
        private val totalDurationUs = this.videoDurationUs + this.audioDurationUs
        private var videoProgressUs = 0L
        private var audioProgressUs = 0L
        private var lastPercent = -1

        fun onVideoProgress(presentationTimeUs: Long) {
            videoProgressUs = maxOf(
                videoProgressUs,
                presentationTimeUs.coerceAtMost(videoDurationUs)
            )
            report()
        }

        fun onAudioProgress(presentationTimeUs: Long) {
            audioProgressUs = maxOf(
                audioProgressUs,
                presentationTimeUs.coerceAtMost(audioDurationUs)
            )
            report()
        }

        fun completeVideo() {
            videoProgressUs = videoDurationUs
            report()
        }

        fun completeAudio() {
            audioProgressUs = audioDurationUs
            report()
        }

        private fun report() {
            if (totalDurationUs <= 0L) {
                return
            }
            val percent = (((videoProgressUs + audioProgressUs) * 100L) / totalDurationUs)
                .toInt()
                .coerceIn(0, 99)
            if (percent != lastPercent) {
                listener.onProgress(percent)
                lastPercent = percent
            }
        }
    }

    private companion object {
        const val VIDEO_PREFIX = "video/"
        const val AUDIO_PREFIX = "audio/"
        const val COPY_BUFFER_SIZE = 256 * 1024
        const val INITIAL_SAMPLE_BUFFER_SIZE = 256 * 1024
    }
}
