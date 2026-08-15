package com.example.bili2media.cache.scanner

import com.example.bili2media.cache.model.BiliCacheStatus
import com.example.bili2media.cache.model.CoverSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileBiliCacheScannerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val scanner = FileBiliCacheScanner()

    @Test
    fun scan_collectsNestedMediaAndExactTotalSize() {
        val root = temporaryFolder.newFolder("input")
        writeText(
            root,
            "cacheA/entry.json",
            """{"title":"Video A","avid":11,"page_data":{"part":"P1","cid":22}}"""
        )
        writeBytes(root, "cacheA/80/video.m4s", byteArrayOf(1, 2, 3))
        writeBytes(root, "cacheA/80/AUDIO.M4S", byteArrayOf(4, 5))
        writeBytes(root, "cacheA/80/ignored.txt", byteArrayOf(6, 7, 8, 9))

        val entries = scanner.scan(root)

        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals("Video A", entry.title)
        assertEquals("P1", entry.subtitle)
        assertEquals(11L, entry.avid)
        assertEquals(22L, entry.cid)
        assertEquals("cacheA", entry.relativePath)
        assertEquals(2, entry.mediaFileCount)
        assertEquals(5L, entry.totalBytes)
        assertEquals(BiliCacheStatus.AVAILABLE, entry.status)
    }

    @Test
    fun scan_marksMalformedMetadataButKeepsMediaEntry() {
        val root = temporaryFolder.newFolder("input")
        writeText(root, "broken/entry.json", "{")
        writeBytes(root, "broken/0.blv", byteArrayOf(1, 2, 3, 4))

        val entry = scanner.scan(root).single()

        assertEquals("broken", entry.title)
        assertEquals(BiliCacheStatus.METADATA_ERROR, entry.status)
        assertEquals(1, entry.mediaFileCount)
        assertEquals(4L, entry.totalBytes)
    }

    @Test
    fun scan_marksValidMetadataWithoutMedia() {
        val root = temporaryFolder.newFolder("input")
        writeText(root, "empty/entry.json", """{"title":"No Media"}""")

        val entry = scanner.scan(root).single()

        assertEquals("No Media", entry.title)
        assertEquals(BiliCacheStatus.NO_MEDIA, entry.status)
        assertEquals(0, entry.mediaFileCount)
        assertEquals(0L, entry.totalBytes)
    }

    @Test
    fun scan_ignoresDirectoriesWithoutEntryJson() {
        val root = temporaryFolder.newFolder("input")
        writeBytes(root, "not-cache/video.mp4", byteArrayOf(1))

        assertTrue(scanner.scan(root).isEmpty())
    }

    @Test
    fun scan_sortsByTitleThenRelativePath() {
        val root = temporaryFolder.newFolder("input")
        writeText(root, "z-path/entry.json", """{"title":"Same"}""")
        writeText(root, "a-path/entry.json", """{"title":"Alpha"}""")
        writeText(root, "b-path/entry.json", """{"title":"Same"}""")

        val entries = scanner.scan(root)

        assertEquals(listOf("Alpha", "Same", "Same"), entries.map { it.title })
        assertEquals(listOf("a-path", "b-path", "z-path"), entries.map { it.relativePath })
    }

    @Test
    fun scan_returnsEmptyForMissingOrNonDirectoryRoot() {
        val root = temporaryFolder.newFolder("input")
        val file = File(root, "single-file").apply { writeText("content") }

        assertTrue(scanner.scan(File(root, "missing")).isEmpty())
        assertTrue(scanner.scan(file).isEmpty())
    }

    @Test
    fun scan_discoversNestedLocalCoverAsFileUri() {
        val root = temporaryFolder.newFolder("input")
        writeText(root, "cacheA/entry.json", """{"title":"Local Cover"}""")
        val cover = writeBytes(root, "cacheA/80/cover.webp", byteArrayOf(1, 2, 3))

        val entry = scanner.scan(root).single()

        assertEquals(CoverSource.Local(cover.toURI().toString()), entry.coverSource)
    }

    @Test
    fun scan_prefersLocalCoverOverMetadataRemoteCover() {
        val root = temporaryFolder.newFolder("input")
        writeText(
            root,
            "cacheA/entry.json",
            """{"title":"Local Wins","cover":"http://i0.hdslb.com/remote.jpg"}"""
        )
        val cover = writeBytes(root, "cacheA/cover.png", byteArrayOf(1))

        val entry = scanner.scan(root).single()

        assertEquals(CoverSource.Local(cover.toURI().toString()), entry.coverSource)
    }

    @Test
    fun scan_usesNormalizedRemoteCoverWhenLocalCoverIsMissing() {
        val root = temporaryFolder.newFolder("input")
        writeText(
            root,
            "cacheA/entry.json",
            """{"title":"Remote Cover","cover":"http://i0.hdslb.com/remote.jpg"}"""
        )

        val entry = scanner.scan(root).single()

        assertEquals(
            CoverSource.Remote("https://i0.hdslb.com/remote.jpg"),
            entry.coverSource
        )
    }

    @Test
    fun scan_doesNotTreatUnrelatedImageAsCover() {
        val root = temporaryFolder.newFolder("input")
        writeText(root, "cacheA/entry.json", """{"title":"No Cover"}""")
        writeBytes(root, "cacheA/poster.jpg", byteArrayOf(1))

        val entry = scanner.scan(root).single()

        assertNull(entry.coverSource)
    }

    @Test
    fun scan_selectsFirstLocalCoverByStableRelativePath() {
        val root = temporaryFolder.newFolder("input")
        writeText(root, "cacheA/entry.json", """{"title":"Stable Cover"}""")
        val expected = writeBytes(root, "cacheA/0/cover.jpeg", byteArrayOf(1))
        writeBytes(root, "cacheA/80/cover.jpg", byteArrayOf(2))

        val entry = scanner.scan(root).single()

        assertEquals(CoverSource.Local(expected.toURI().toString()), entry.coverSource)
    }

    @Test
    fun scan_doesNotIncludeNestedCacheAssetsInParentEntry() {
        val root = temporaryFolder.newFolder("input")
        writeText(
            root,
            "parent/entry.json",
            """{"title":"Parent","cover":"https://i0.hdslb.com/parent.jpg"}"""
        )
        writeText(root, "parent/child/entry.json", """{"title":"Child"}""")
        val childCover = writeBytes(root, "parent/child/cover.webp", byteArrayOf(1))
        writeBytes(root, "parent/child/video.m4s", byteArrayOf(2, 3))

        val entries = scanner.scan(root)
        val parent = entries.single { it.relativePath == "parent" }
        val child = entries.single { it.relativePath == "parent/child" }

        assertEquals(CoverSource.Remote("https://i0.hdslb.com/parent.jpg"), parent.coverSource)
        assertEquals(0, parent.mediaFileCount)
        assertEquals(0L, parent.totalBytes)
        assertEquals(CoverSource.Local(childCover.toURI().toString()), child.coverSource)
        assertEquals(1, child.mediaFileCount)
        assertEquals(2L, child.totalBytes)
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
