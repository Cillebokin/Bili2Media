package com.example.bili2media.export.m4a.engine

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bili2media.cache.model.MediaInputRef
import com.example.bili2media.export.m4a.model.M4aExportPlan
import com.example.bili2media.export.m4a.output.MediaStoreM4aOutputStore
import com.example.bili2media.export.model.MediaTrackSelection
import com.example.bili2media.testmedia.TestMediaFixtureFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class AndroidM4aExportEngineTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val resolver = context.contentResolver
    private val engine = AndroidM4aExportEngine(context)
    private val outputStore = MediaStoreM4aOutputStore(context)
    private val fixtures = TestMediaFixtureFactory(context)

    @Test
    fun export_copiesExistingM4aBytesAndReportsCompletion() {
        val fixture = fixtures.createAudioOnlyM4s()
        val source = File(context.cacheDir, "copy-${System.nanoTime()}.m4a")
        fixture.copyTo(source)
        fixture.delete()
        val output = outputStore.create("Copy-${System.nanoTime()}")
        val listener = RecordingListener()
        try {
            val result = engine.export(
                M4aExportPlan.CopyExistingM4a(
                    MediaInputRef.FilePath(source.absolutePath),
                    source.length(),
                    readAudioTrack(source).durationUs
                ),
                output.uri,
                listener
            )

            assertEquals(M4aEngineResult.Success, result)
            val actual = resolver.openInputStream(Uri.parse(output.uri))!!.use { it.readBytes() }
            assertArrayEquals(source.readBytes(), actual)
            assertEquals(100, listener.progress.last())
        } finally {
            outputStore.abandon(output)
            source.delete()
        }
    }

    @Test
    fun export_remuxesAudioM4sToOneAacTrack() {
        val source = fixtures.createAudioOnlyM4s()
        try {
            assertRemuxesAudioOnly(source)
        } finally {
            source.delete()
        }
    }

    @Test
    fun export_extractsOnlyAacTrackFromCombinedMp4() {
        val source = fixtures.createCombinedMp4()
        try {
            assertRemuxesAudioOnly(source)
        } finally {
            source.delete()
        }
    }

    @Test
    fun export_reportsCompletionAfterOutputAndExtractorSessionClose() {
        val source = fixtures.createAudioOnlyM4s()
        val closer = RecordingResourceCloser()
        val closingEngine = AndroidM4aExportEngine(context, closer::close)
        val output = outputStore.create("Close-order-${System.nanoTime()}")
        val listener = CompletionOrderListener { closer.closedResources }
        try {
            val sourceTrack = readAudioTrack(source)
            val result = closingEngine.export(
                M4aExportPlan.RemuxAacTrack(
                    MediaTrackSelection(
                        MediaInputRef.FilePath(source.absolutePath),
                        sourceTrack.index,
                        sourceTrack.durationUs
                    )
                ),
                output.uri,
                listener
            )

            assertEquals(M4aEngineResult.Success, result)
            assertEquals(2, listener.closedResourcesWhenCompletion)
            assertTerminalProgress(listener.progress)
        } finally {
            outputStore.abandon(output)
            source.delete()
        }
    }

    @Test
    fun export_mapsOutputDescriptorCloseFailureWithoutReportingCompletion() {
        val source = fixtures.createAudioOnlyM4s()
        val closingEngine = AndroidM4aExportEngine(context) { resource ->
            val closed = runCatching { resource.close() }.isSuccess
            if (resource is ParcelFileDescriptor) false else closed
        }
        val output = outputStore.create("Close-failure-${System.nanoTime()}")
        val listener = RecordingListener()
        try {
            val sourceTrack = readAudioTrack(source)
            val result = closingEngine.export(
                M4aExportPlan.RemuxAacTrack(
                    MediaTrackSelection(
                        MediaInputRef.FilePath(source.absolutePath),
                        sourceTrack.index,
                        sourceTrack.durationUs
                    )
                ),
                output.uri,
                listener
            )

            assertEquals(
                M4aEngineResult.Failure(M4aEngineError.MUXER_FAILED),
                result
            )
            assertTrue(listener.progress.none { it == 100 })
            assertTrue(listener.progress.all { it <= 99 })
        } finally {
            outputStore.abandon(output)
            source.delete()
        }
    }

    private fun assertRemuxesAudioOnly(source: File) {
        val sourceTrack = readAudioTrack(source)
        val output = outputStore.create("Remux-${System.nanoTime()}")
        val listener = RecordingListener()
        try {
            val result = engine.export(
                M4aExportPlan.RemuxAacTrack(
                    MediaTrackSelection(
                        MediaInputRef.FilePath(source.absolutePath),
                        sourceTrack.index,
                        sourceTrack.durationUs
                    )
                ),
                output.uri,
                listener
            )

            assertEquals(M4aEngineResult.Success, result)
            val outputTrack = readAudioTrack(Uri.parse(output.uri))
            assertEquals(sourceTrack.sampleRate, outputTrack.sampleRate)
            assertEquals(sourceTrack.channelCount, outputTrack.channelCount)
            assertTerminalProgress(listener.progress)
        } finally {
            outputStore.abandon(output)
        }
    }

    private fun readAudioTrack(file: File): AudioTrack {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            readAudioTrack(extractor)
        } finally {
            extractor.release()
        }
    }

    private fun readAudioTrack(uri: Uri): AudioTrack {
        val descriptor = requireNotNull(resolver.openAssetFileDescriptor(uri, "r"))
        val extractor = MediaExtractor()
        return try {
            descriptor.use {
                if (it.declaredLength >= 0L) {
                    extractor.setDataSource(it.fileDescriptor, it.startOffset, it.declaredLength)
                } else {
                    extractor.setDataSource(it.fileDescriptor)
                }
                assertEquals(1, extractor.trackCount)
                val track = readAudioTrack(extractor)
                extractor.selectTrack(track.index)
                assertAllSampleTimesAreNonNegative(extractor)
                track
            }
        } finally {
            extractor.release()
        }
    }

    private fun assertAllSampleTimesAreNonNegative(extractor: MediaExtractor) {
        val buffer = ByteBuffer.allocate(256 * 1024)
        var firstSample = true
        while (true) {
            buffer.clear()
            if (extractor.readSampleData(buffer, 0) < 0) {
                return
            }
            assertTrue(extractor.sampleTime >= 0L)
            if (firstSample) {
                assertEquals(0L, extractor.sampleTime)
                firstSample = false
            }
            if (!extractor.advance()) {
                return
            }
        }
    }

    private fun assertTerminalProgress(progress: List<Int>) {
        assertEquals(100, progress.last())
        assertTrue(progress.dropLast(1).all { it <= 99 })
    }

    private fun readAudioTrack(extractor: MediaExtractor): AudioTrack {
        val audioIndices = (0 until extractor.trackCount).filter { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                ?.startsWith("audio/") == true
        }
        assertEquals(1, audioIndices.size)
        val index = audioIndices.single()
        val format = extractor.getTrackFormat(index)
        assertEquals("audio/mp4a-latm", format.getString(MediaFormat.KEY_MIME))
        return AudioTrack(
            index = index,
            durationUs = format.getLong(MediaFormat.KEY_DURATION),
            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
            channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        )
    }

    private class RecordingListener : M4aEngineListener {
        val progress = mutableListOf<Int>()
        override fun onProgress(percent: Int) {
            progress += percent
        }
        override fun isCancelled(): Boolean = false
    }

    private class CompletionOrderListener(
        private val closedResourceCount: () -> Int
    ) : M4aEngineListener {
        val progress = mutableListOf<Int>()
        var closedResourcesWhenCompletion: Int? = null

        override fun onProgress(percent: Int) {
            progress += percent
            if (percent == 100) {
                closedResourcesWhenCompletion = closedResourceCount()
            }
        }

        override fun isCancelled(): Boolean = false
    }

    private class RecordingResourceCloser {
        var closedResources = 0
            private set

        fun close(resource: AutoCloseable): Boolean {
            val closed = runCatching { resource.close() }.isSuccess
            closedResources += 1
            return closed
        }
    }

    private data class AudioTrack(
        val index: Int,
        val durationUs: Long,
        val sampleRate: Int,
        val channelCount: Int
    )
}
