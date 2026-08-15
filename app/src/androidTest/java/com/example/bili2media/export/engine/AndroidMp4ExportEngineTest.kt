package com.example.bili2media.export.engine

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bili2media.cache.model.MediaInputRef
import com.example.bili2media.export.model.MediaTrackSelection
import com.example.bili2media.export.model.Mp4ExportPlan
import com.example.bili2media.export.output.MediaStoreMp4OutputStore
import com.example.bili2media.testmedia.TestMediaFixtureFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AndroidMp4ExportEngineTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val contentResolver = context.contentResolver
    private val engine = AndroidMp4ExportEngine(context)
    private val outputStore = MediaStoreMp4OutputStore(context)
    private val fixtures = TestMediaFixtureFactory(context)

    @Test
    fun export_copiesExistingMp4BytesAndReportsCompletion() {
        val sourceBytes = ByteArray(700_000) { index -> (index % 251).toByte() }
        val source = File(context.cacheDir, "copy-source-${System.nanoTime()}.mp4")
        source.writeBytes(sourceBytes)
        val output = outputStore.create("Copy-${System.nanoTime()}")
        val listener = RecordingListener()
        try {
            val result = engine.export(
                plan = Mp4ExportPlan.CopyExistingMp4(
                    input = MediaInputRef.FilePath(source.absolutePath),
                    sourceBytes = source.length(),
                    durationUs = 1_000_000L
                ),
                outputUri = output.uri,
                listener = listener
            )

            assertEquals(Mp4EngineResult.Success, result)
            val actualBytes = contentResolver.openInputStream(Uri.parse(output.uri))!!.use {
                it.readBytes()
            }
            assertArrayEquals(sourceBytes, actualBytes)
            assertEquals(100, listener.progress.last())
        } finally {
            outputStore.abandon(output)
            source.delete()
        }
    }

    @Test
    fun export_muxesVideoAndAudioM4sWithNonNegativeTimestamps() {
        val video = fixtures.createVideoOnlyM4s()
        val audio = fixtures.createAudioOnlyM4s()
        val videoTrack = readSingleTrack(video)
        val audioTrack = readSingleTrack(audio)
        val output = outputStore.create("Mux-${System.nanoTime()}")
        val listener = RecordingListener()
        try {
            val result = engine.export(
                plan = Mp4ExportPlan.MuxM4s(
                    video = MediaTrackSelection(
                        input = MediaInputRef.FilePath(video.absolutePath),
                        trackIndex = videoTrack.index,
                        durationUs = videoTrack.durationUs
                    ),
                    audio = MediaTrackSelection(
                        input = MediaInputRef.FilePath(audio.absolutePath),
                        trackIndex = audioTrack.index,
                        durationUs = audioTrack.durationUs
                    ),
                    durationUs = maxOf(videoTrack.durationUs, audioTrack.durationUs)
                ),
                outputUri = output.uri,
                listener = listener
            )

            assertEquals(Mp4EngineResult.Success, result)
            assertOutputTracks(output.uri)
            assertEquals(100, listener.progress.last())
        } finally {
            outputStore.abandon(output)
            video.delete()
            audio.delete()
        }
    }

    private fun readSingleTrack(file: File): FixtureTrack {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            assertEquals(1, extractor.trackCount)
            val format = extractor.getTrackFormat(0)
            FixtureTrack(
                index = 0,
                durationUs = format.getLong(MediaFormat.KEY_DURATION)
            )
        } finally {
            extractor.release()
        }
    }

    private fun assertOutputTracks(uriValue: String) {
        val descriptor = requireNotNull(
            contentResolver.openAssetFileDescriptor(Uri.parse(uriValue), "r")
        )
        val extractor = MediaExtractor()
        try {
            descriptor.use {
                if (it.declaredLength >= 0L) {
                    extractor.setDataSource(
                        it.fileDescriptor,
                        it.startOffset,
                        it.declaredLength
                    )
                } else {
                    extractor.setDataSource(it.fileDescriptor)
                }
                assertEquals(2, extractor.trackCount)
                val mimeTypes = (0 until extractor.trackCount).map { trackIndex ->
                    extractor.getTrackFormat(trackIndex).getString(MediaFormat.KEY_MIME)
                }.toSet()
                assertEquals(setOf("video/avc", "audio/mp4a-latm"), mimeTypes)

                repeat(extractor.trackCount) { trackIndex ->
                    extractor.selectTrack(trackIndex)
                    assertTrue(extractor.sampleTime >= 0L)
                    extractor.unselectTrack(trackIndex)
                    extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                }
            }
        } finally {
            extractor.release()
        }
    }

    private class RecordingListener : Mp4EngineListener {
        val progress = mutableListOf<Int>()

        override fun onProgress(percent: Int) {
            progress += percent
        }

        override fun isCancelled(): Boolean = false
    }

    private data class FixtureTrack(
        val index: Int,
        val durationUs: Long
    )
}
