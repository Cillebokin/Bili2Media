package com.example.bili2media.cache.media

import com.example.bili2media.cache.model.CacheEntryLocation
import com.example.bili2media.cache.model.MediaInputRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileCacheMediaLocatorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val locator = FileCacheMediaLocator()

    @Test
    fun locate_returnsOnlyOwnedMp4AndM4sFilesInStablePathOrder() {
        val candidate = temporaryFolder.newFolder("cacheA")
        writeText(candidate, "entry.json", "{}")
        val audio = writeBytes(candidate, "80/audio.M4S", byteArrayOf(1, 2))
        val video = writeBytes(candidate, "80/video.m4s", byteArrayOf(3, 4, 5))
        val existing = writeBytes(candidate, "existing.mp4", byteArrayOf(6, 7, 8, 9))
        writeBytes(candidate, "ignored.blv", byteArrayOf(10))
        writeBytes(candidate, "cover.jpg", byteArrayOf(11))
        writeText(candidate, "nested/entry.json", "{}")
        writeBytes(candidate, "nested/video.m4s", byteArrayOf(12))

        val files = locator.locate(
            CacheEntryLocation.FileDirectory(candidate.canonicalPath)
        )

        assertEquals(
            listOf("80/audio.M4S", "80/video.m4s", "existing.mp4"),
            files.map { it.relativePath }
        )
        assertEquals(listOf(2L, 3L, 4L), files.map { it.size })
        assertEquals(
            listOf(audio, video, existing).map { it.canonicalPath },
            files.map { (it.input as MediaInputRef.FilePath).path }
        )
    }

    @Test
    fun locate_returnsEmptyForMissingDirectoryOrDifferentLocationType() {
        val root = temporaryFolder.newFolder("root")

        assertTrue(
            locator.locate(
                CacheEntryLocation.FileDirectory(File(root, "missing").absolutePath)
            ).isEmpty()
        )
        assertTrue(
            locator.locate(
                CacheEntryLocation.DocumentDirectory("content://bili2media.test/cache")
            ).isEmpty()
        )
    }

    private fun writeText(root: File, relativePath: String, content: String) {
        val target = File(root, relativePath)
        target.parentFile?.mkdirs()
        target.writeText(content)
    }

    private fun writeBytes(root: File, relativePath: String, content: ByteArray): File {
        val target = File(root, relativePath)
        target.parentFile?.mkdirs()
        target.writeBytes(content)
        return target
    }
}
