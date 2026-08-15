package com.example.bili2media.media.probe

import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.bili2media.cache.model.CacheMediaFile
import com.example.bili2media.cache.model.MediaInputRef
import com.example.bili2media.media.model.MediaTrackInfo
import com.example.bili2media.media.model.MediaTrackKind
import com.example.bili2media.media.model.ProbedMediaFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class AndroidMediaExtractorProbe(
    context: Context
) : MediaProbe {
    private val contentResolver: ContentResolver = context.applicationContext.contentResolver

    override fun probe(file: CacheMediaFile): MediaProbeResult {
        val extractor = MediaExtractor()
        var sourceAvailable = false
        var dataSourceConfigured = false
        return try {
            when (val input = file.input) {
                is MediaInputRef.FilePath -> {
                    val source = File(input.path)
                    if (!source.isFile || !source.canRead()) {
                        return MediaProbeResult.Failure(
                            file = file,
                            reason = MediaProbeFailure.INPUT_UNAVAILABLE
                        )
                    }
                    sourceAvailable = true
                    extractor.setDataSource(source.absolutePath)
                    dataSourceConfigured = true
                    readTracks(file, extractor)
                }

                is MediaInputRef.ContentUri -> {
                    val descriptor = openDescriptor(input.uri)
                        ?: return MediaProbeResult.Failure(
                            file = file,
                            reason = MediaProbeFailure.INPUT_UNAVAILABLE
                        )
                    descriptor.use {
                        sourceAvailable = true
                        extractor.setDescriptorDataSource(it)
                        dataSourceConfigured = true
                        readTracks(file, extractor)
                    }
                }
            }
        } catch (_: FileNotFoundException) {
            MediaProbeResult.Failure(file, MediaProbeFailure.INPUT_UNAVAILABLE)
        } catch (_: SecurityException) {
            MediaProbeResult.Failure(file, MediaProbeFailure.INPUT_UNAVAILABLE)
        } catch (_: IOException) {
            MediaProbeResult.Failure(
                file,
                failureForStage(sourceAvailable, dataSourceConfigured)
            )
        } catch (_: IllegalArgumentException) {
            MediaProbeResult.Failure(
                file,
                failureForStage(sourceAvailable, dataSourceConfigured)
            )
        } catch (_: RuntimeException) {
            MediaProbeResult.Failure(
                file,
                failureForStage(sourceAvailable, dataSourceConfigured)
            )
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun openDescriptor(uriValue: String): AssetFileDescriptor? {
        if (uriValue.isBlank()) {
            return null
        }
        return contentResolver.openAssetFileDescriptor(Uri.parse(uriValue), "r")
    }

    private fun MediaExtractor.setDescriptorDataSource(descriptor: AssetFileDescriptor) {
        val length = descriptor.declaredLength
        if (length >= 0L) {
            setDataSource(
                descriptor.fileDescriptor,
                descriptor.startOffset,
                length
            )
        } else {
            setDataSource(descriptor.fileDescriptor)
        }
    }

    private fun readTracks(
        file: CacheMediaFile,
        extractor: MediaExtractor
    ): MediaProbeResult.Success {
        val tracks = List(extractor.trackCount) { index ->
            val format = extractor.getTrackFormat(index)
            val mimeType = format.getString(MediaFormat.KEY_MIME).orEmpty()
            MediaTrackInfo(
                index = index,
                kind = MediaTrackKind.fromMime(mimeType),
                mimeType = mimeType,
                durationUs = format.longValueOrNull(MediaFormat.KEY_DURATION),
                width = format.intValueOrNull(MediaFormat.KEY_WIDTH),
                height = format.intValueOrNull(MediaFormat.KEY_HEIGHT),
                bitrate = format.intValueOrNull(MediaFormat.KEY_BIT_RATE)
            )
        }
        return MediaProbeResult.Success(
            ProbedMediaFile(file = file, tracks = tracks)
        )
    }

    private fun MediaFormat.longValueOrNull(key: String): Long? {
        return if (containsKey(key)) getLong(key) else null
    }

    private fun MediaFormat.intValueOrNull(key: String): Int? {
        return if (containsKey(key)) getInteger(key) else null
    }

    private fun failureForStage(
        sourceAvailable: Boolean,
        dataSourceConfigured: Boolean
    ): MediaProbeFailure {
        return when {
            !sourceAvailable -> MediaProbeFailure.INPUT_UNAVAILABLE
            !dataSourceConfigured -> MediaProbeFailure.MALFORMED_MEDIA
            else -> MediaProbeFailure.READ_FAILED
        }
    }
}
