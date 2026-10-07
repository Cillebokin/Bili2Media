package com.example.bili2media.export.m4a.engine

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
import com.example.bili2media.export.m4a.model.M4aExportPlan
import com.example.bili2media.export.model.MediaTrackSelection
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.ByteBuffer

class AndroidM4aExportEngine(
    context: Context
) : M4aExportEngine {
    private val contentResolver: ContentResolver = context.applicationContext.contentResolver
    private var closeResource: (AutoCloseable) -> Boolean = ::closeQuietly

    internal constructor(
        context: Context,
        closeResource: (AutoCloseable) -> Boolean
    ) : this(context) {
        this.closeResource = closeResource
    }

    override fun export(
        plan: M4aExportPlan,
        outputUri: String,
        listener: M4aEngineListener
    ): M4aEngineResult {
        return when (plan) {
            is M4aExportPlan.CopyExistingM4a -> copy(plan, outputUri, listener)
            is M4aExportPlan.RemuxAacTrack -> remux(plan, outputUri, listener)
        }
    }

    private fun copy(
        plan: M4aExportPlan.CopyExistingM4a,
        outputUri: String,
        listener: M4aEngineListener
    ): M4aEngineResult {
        if (listener.isCancelled()) {
            return M4aEngineResult.Cancelled
        }
        val input = try {
            openInputStream(plan.input)
        } catch (_: Exception) {
            return M4aEngineResult.Failure(M4aEngineError.INPUT_OPEN_FAILED)
        }
        val result = try {
            input.use { source ->
                val output = try {
                    contentResolver.openOutputStream(Uri.parse(outputUri), "w")
                        ?: return@use M4aEngineResult.Failure(M4aEngineError.OUTPUT_OPEN_FAILED)
                } catch (_: Exception) {
                    return@use M4aEngineResult.Failure(M4aEngineError.OUTPUT_OPEN_FAILED)
                }
                output.use { destination ->
                    try {
                        val buffer = ByteArray(COPY_BUFFER_SIZE)
                        var copiedBytes = 0L
                        var lastProgress = -1
                        while (true) {
                            if (listener.isCancelled()) {
                                return@use M4aEngineResult.Cancelled
                            }
                            val bytesRead = source.read(buffer)
                            if (bytesRead < 0) {
                                break
                            }
                            destination.write(buffer, 0, bytesRead)
                            copiedBytes += bytesRead
                            reportCopyProgress(
                                copiedBytes = copiedBytes,
                                sourceBytes = plan.sourceBytes,
                                lastProgress = lastProgress,
                                listener = listener
                            ).also { lastProgress = it }
                        }
                        destination.flush()
                        if (listener.isCancelled()) {
                            M4aEngineResult.Cancelled
                        } else {
                            M4aEngineResult.Success
                        }
                    } catch (_: Exception) {
                        M4aEngineResult.Failure(M4aEngineError.COPY_FAILED)
                    }
                }
            }
        } catch (_: Exception) {
            M4aEngineResult.Failure(M4aEngineError.COPY_FAILED)
        }
        if (result == M4aEngineResult.Success) {
            listener.onProgress(100)
        }
        return result
    }

    private fun remux(
        plan: M4aExportPlan.RemuxAacTrack,
        outputUri: String,
        listener: M4aEngineListener
    ): M4aEngineResult {
        if (listener.isCancelled()) {
            return M4aEngineResult.Cancelled
        }
        val session = openExtractorOrNull(plan.selection.input)
            ?: return M4aEngineResult.Failure(M4aEngineError.INPUT_OPEN_FAILED)
        var result: M4aEngineResult = M4aEngineResult.Failure(M4aEngineError.MUXER_FAILED)
        try {
            val format = session.aacTrackFormatOrNull(plan.selection)
            if (format == null) {
                result = M4aEngineResult.Failure(M4aEngineError.INVALID_TRACK)
            } else {
                val outputDescriptor = openOutputDescriptorOrNull(outputUri)
                if (outputDescriptor == null) {
                    result = M4aEngineResult.Failure(M4aEngineError.OUTPUT_OPEN_FAILED)
                } else {
                    try {
                        val muxer = try {
                            MediaMuxer(
                                outputDescriptor.fileDescriptor,
                                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                            )
                        } catch (_: Exception) {
                            null
                        }
                        result = if (muxer == null) {
                            M4aEngineResult.Failure(M4aEngineError.OUTPUT_OPEN_FAILED)
                        } else {
                            runRemux(muxer, session, plan.selection, format, listener)
                        }
                    } finally {
                        if (!closeSafely(outputDescriptor)) {
                            result = M4aEngineResult.Failure(M4aEngineError.MUXER_FAILED)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            result = M4aEngineResult.Failure(M4aEngineError.MUXER_FAILED)
        } finally {
            if (!closeSafely(session)) {
                result = M4aEngineResult.Failure(M4aEngineError.MUXER_FAILED)
            }
        }
        if (result == M4aEngineResult.Success) {
            listener.onProgress(100)
        }
        return result
    }

    private fun runRemux(
        muxer: MediaMuxer,
        session: ExtractorSession,
        selection: MediaTrackSelection,
        format: MediaFormat,
        listener: M4aEngineListener
    ): M4aEngineResult {
        var muxerStarted = false
        var result: M4aEngineResult = M4aEngineResult.Failure(M4aEngineError.MUXER_FAILED)
        try {
            val outputTrackIndex = muxer.addTrack(format)
            muxer.start()
            muxerStarted = true
            result = copyAacTrack(
                session = session,
                selection = selection,
                muxer = muxer,
                outputTrackIndex = outputTrackIndex,
                listener = listener
            )
        } catch (_: IllegalArgumentException) {
            result = M4aEngineResult.Failure(
                if (muxerStarted) M4aEngineError.MUXER_FAILED else M4aEngineError.INVALID_TRACK
            )
        } catch (_: Exception) {
            result = M4aEngineResult.Failure(M4aEngineError.MUXER_FAILED)
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
            if (closeFailed && result == M4aEngineResult.Success) {
                result = M4aEngineResult.Failure(M4aEngineError.MUXER_FAILED)
            }
        }
        return result
    }

    private fun copyAacTrack(
        session: ExtractorSession,
        selection: MediaTrackSelection,
        muxer: MediaMuxer,
        outputTrackIndex: Int,
        listener: M4aEngineListener
    ): M4aEngineResult {
        val extractor = session.extractor
        extractor.selectTrack(selection.trackIndex)
        var buffer = ByteBuffer.allocate(INITIAL_SAMPLE_BUFFER_SIZE)
        val bufferInfo = MediaCodec.BufferInfo()
        val progress = RemuxProgress(selection.durationUs, listener)
        var firstPresentationTimeUs: Long? = null
        var lastPresentationTimeUs = 0L
        try {
            while (true) {
                if (listener.isCancelled()) {
                    return M4aEngineResult.Cancelled
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
                val normalizedTimeUs = (sourceTimeUs - firstTimeUs)
                    .coerceAtLeast(0L)
                    .coerceAtLeast(lastPresentationTimeUs)
                bufferInfo.set(
                    0,
                    sampleSize,
                    normalizedTimeUs,
                    extractor.sampleFlags.toMuxerFlags()
                )
                muxer.writeSampleData(outputTrackIndex, buffer, bufferInfo)
                lastPresentationTimeUs = normalizedTimeUs
                progress.onSample(normalizedTimeUs)
                if (!extractor.advance()) {
                    break
                }
            }
            return if (listener.isCancelled()) {
                M4aEngineResult.Cancelled
            } else {
                M4aEngineResult.Success
            }
        } finally {
            runCatching { extractor.unselectTrack(selection.trackIndex) }
        }
    }

    private fun reportCopyProgress(
        copiedBytes: Long,
        sourceBytes: Long,
        lastProgress: Int,
        listener: M4aEngineListener
    ): Int {
        if (sourceBytes <= 0L) {
            return lastProgress
        }
        val progress = ((copiedBytes * 100L) / sourceBytes)
            .toInt()
            .coerceIn(0, 99)
        if (progress != lastProgress) {
            listener.onProgress(progress)
        }
        return progress
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

    private fun closeSafely(resource: AutoCloseable): Boolean {
        return runCatching { closeResource(resource) }.getOrDefault(false)
    }

    private fun ExtractorSession.aacTrackFormatOrNull(
        selection: MediaTrackSelection
    ): MediaFormat? {
        if (selection.trackIndex !in 0 until extractor.trackCount) {
            return null
        }
        val format = runCatching { extractor.getTrackFormat(selection.trackIndex) }.getOrNull()
            ?: return null
        val mimeType = runCatching { format.getString(MediaFormat.KEY_MIME) }.getOrNull()
            ?: return null
        return format.takeIf { mimeType == AAC_MIME }
    }

    private class ExtractorSession private constructor(
        val extractor: MediaExtractor,
        private val descriptor: AssetFileDescriptor?
    ) : Closeable {
        override fun close() {
            var closeFailure: Throwable? = null
            runCatching { extractor.release() }
                .onFailure { closeFailure = it }
            runCatching { descriptor?.close() }
                .onFailure { error ->
                    val previousFailure = closeFailure
                    if (previousFailure == null) {
                        closeFailure = error
                    } else {
                        previousFailure.addSuppressed(error)
                    }
                }
            closeFailure?.let { throw it }
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

    private class RemuxProgress(
        durationUs: Long,
        private val listener: M4aEngineListener
    ) {
        private val durationUs = durationUs.coerceAtLeast(0L)
        private var lastPercent = -1

        fun onSample(presentationTimeUs: Long) {
            if (durationUs <= 0L) {
                return
            }
            val percent = ((presentationTimeUs.coerceAtMost(durationUs) * 100L) / durationUs)
                .toInt()
                .coerceIn(0, 99)
            if (percent != lastPercent) {
                listener.onProgress(percent)
                lastPercent = percent
            }
        }
    }

    private companion object {
        const val AAC_MIME = "audio/mp4a-latm"
        const val COPY_BUFFER_SIZE = 256 * 1024
        const val INITIAL_SAMPLE_BUFFER_SIZE = 256 * 1024

        fun closeQuietly(resource: AutoCloseable): Boolean {
            return runCatching { resource.close() }.isSuccess
        }
    }
}
