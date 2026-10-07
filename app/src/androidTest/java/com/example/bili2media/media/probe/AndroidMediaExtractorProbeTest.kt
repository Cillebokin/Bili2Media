package com.example.bili2media.media.probe

import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bili2media.cache.model.CacheMediaFile
import com.example.bili2media.cache.model.MediaInputRef
import com.example.bili2media.media.model.MediaTrackKind
import com.example.bili2media.testmedia.TestMediaFixtureFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AndroidMediaExtractorProbeTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val probe = AndroidMediaExtractorProbe(context)
    private val fixtures = TestMediaFixtureFactory(context)

    @Test
    fun probe_readsVideoTrackFromFileM4s() {
        val source = fixtures.createVideoOnlyM4s()
        try {
            val mediaFile = source.toCacheMediaFile()

            val result = probe.probe(mediaFile)

            val media = (result as MediaProbeResult.Success).media
            assertEquals(mediaFile, media.file)
            assertEquals(1, media.tracks.size)
            with(media.tracks.single()) {
                assertEquals(0, index)
                assertEquals(MediaTrackKind.VIDEO, kind)
                assertEquals("video/avc", mimeType)
                assertEquals(TestMediaFixtureFactory.VIDEO_WIDTH, width)
                assertEquals(TestMediaFixtureFactory.VIDEO_HEIGHT, height)
                assertNotNull(durationUs)
                assertTrue(requireNotNull(durationUs) > 0L)
            }
        } finally {
            source.delete()
        }
    }

    @Test
    fun probe_readsVideoAndAudioTracksFromContentUri() {
        val source = fixtures.createCombinedMp4()
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "combined-${System.nanoTime()}.mp4")
            put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/Bili2Media/Test")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = requireNotNull(
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        )
        try {
            context.contentResolver.openOutputStream(uri, "w")!!.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            }
            val mediaFile = CacheMediaFile(
                name = "combined.mp4",
                relativePath = "combined.mp4",
                size = source.length(),
                input = MediaInputRef.ContentUri(uri.toString())
            )

            val result = probe.probe(mediaFile)

            val tracks = (result as MediaProbeResult.Success).media.tracks
            assertEquals(2, tracks.size)
            assertEquals(
                setOf(MediaTrackKind.VIDEO, MediaTrackKind.AUDIO),
                tracks.map { it.kind }.toSet()
            )
            assertTrue(tracks.all { it.durationUs != null && it.durationUs > 0L })
            val audio = tracks.single { it.kind == MediaTrackKind.AUDIO }
            assertTrue(requireNotNull(audio.sampleRate) > 0)
            assertTrue(requireNotNull(audio.channelCount) > 0)
        } finally {
            context.contentResolver.delete(uri, null, null)
            source.delete()
        }
    }

    @Test
    fun probe_returnsInputUnavailableForMissingFile() {
        val missing = File(context.cacheDir, "missing-${System.nanoTime()}.m4s")
        val mediaFile = CacheMediaFile(
            name = missing.name,
            relativePath = missing.name,
            size = 0L,
            input = MediaInputRef.FilePath(missing.absolutePath)
        )

        val result = probe.probe(mediaFile)

        assertEquals(
            MediaProbeFailure.INPUT_UNAVAILABLE,
            (result as MediaProbeResult.Failure).reason
        )
    }

    @Test
    fun probe_returnsMalformedMediaForInvalidFileBytes() {
        val source = File(context.cacheDir, "invalid-${System.nanoTime()}.m4s")
        try {
            source.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
            val mediaFile = CacheMediaFile(
                name = source.name,
                relativePath = source.name,
                size = source.length(),
                input = MediaInputRef.FilePath(source.absolutePath)
            )

            val result = probe.probe(mediaFile)

            assertEquals(
                MediaProbeFailure.MALFORMED_MEDIA,
                (result as MediaProbeResult.Failure).reason
            )
        } finally {
            source.delete()
        }
    }

    @Test
    fun probe_opensContentUriAndReportsMalformedMedia() {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "invalid-${System.nanoTime()}.m4s")
            put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/Bili2Media/Test")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = requireNotNull(
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        )
        try {
            context.contentResolver.openOutputStream(uri, "w")!!.use { output ->
                output.write(byteArrayOf(6, 7, 8, 9))
            }
            val mediaFile = CacheMediaFile(
                name = "invalid.m4s",
                relativePath = "invalid.m4s",
                size = 4L,
                input = MediaInputRef.ContentUri(uri.toString())
            )

            val result = probe.probe(mediaFile)

            assertTrue(result is MediaProbeResult.Failure)
            assertEquals(
                MediaProbeFailure.MALFORMED_MEDIA,
                (result as MediaProbeResult.Failure).reason
            )
        } finally {
            context.contentResolver.delete(uri, null, null)
        }
    }

    private fun File.toCacheMediaFile(): CacheMediaFile {
        return CacheMediaFile(
            name = name,
            relativePath = name,
            size = length(),
            input = MediaInputRef.FilePath(absolutePath)
        )
    }
}
