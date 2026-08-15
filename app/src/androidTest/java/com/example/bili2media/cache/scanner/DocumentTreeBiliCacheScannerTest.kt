package com.example.bili2media.cache.scanner

import androidx.documentfile.provider.FakeDocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bili2media.cache.model.CacheEntryLocation
import com.example.bili2media.cache.model.CoverSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentTreeBiliCacheScannerTest {
    @Test
    fun scan_keepsNestedCacheAssetsOwnedByNestedEntry() {
        val parentEntry = FakeDocumentFile.file("parent-entry", "entry.json", 1)
        val childEntry = FakeDocumentFile.file("child-entry", "entry.json", 1)
        val childCover = FakeDocumentFile.file("child-cover", "cover.webp", 4)
        val childMedia = FakeDocumentFile.file("child-media", "video.m4s", 6)
        val child = FakeDocumentFile.directory("child", "child")
            .add(childEntry)
            .add(childCover)
            .add(childMedia)
        val parent = FakeDocumentFile.directory("parent", "parent")
            .add(parentEntry)
            .add(child)
        val root = FakeDocumentFile.directory("root", "selected").add(parent)
        val scanner = scanner(
            parentEntry to """{"title":"Parent","cover":"https://i0.hdslb.com/parent.jpg"}""",
            childEntry to """{"title":"Child"}"""
        )

        val entries = scanner.scan(root)
        val parentResult = entries.single { it.title == "Parent" }
        val childResult = entries.single { it.title == "Child" }

        assertEquals(CoverSource.Remote("https://i0.hdslb.com/parent.jpg"), parentResult.coverSource)
        assertEquals(0, parentResult.mediaFileCount)
        assertEquals(CoverSource.Local(childCover.uri.toString()), childResult.coverSource)
        assertEquals(1, childResult.mediaFileCount)
        assertEquals(6L, childResult.totalBytes)
    }

    @Test
    fun scan_selectsLocalCoverByStableRelativePath() {
        val entry = FakeDocumentFile.file("stable-entry", "entry.json", 1)
        val expected = FakeDocumentFile.file("a-cover", "cover.jpg", 1)
        val candidate = FakeDocumentFile.directory("stable", "stable")
            .add(entry)
            .add(
                FakeDocumentFile.directory("z", "z")
                    .add(FakeDocumentFile.file("z-cover", "cover.webp", 1))
            )
            .add(FakeDocumentFile.directory("a", "a").add(expected))
        val root = FakeDocumentFile.directory("root-stable", "selected").add(candidate)

        val result = scanner(entry to """{"title":"Stable"}""").scan(root).single()

        assertEquals(CoverSource.Local(expected.uri.toString()), result.coverSource)
        assertEquals(
            CacheEntryLocation.DocumentDirectory(candidate.uri.toString()),
            result.location
        )
    }

    @Test
    fun scan_prefersExactLowercaseEntryMetadataFile() {
        val uppercaseEntry = FakeDocumentFile.file("upper-entry", "ENTRY.JSON", 1)
        val exactEntry = FakeDocumentFile.file("exact-entry", "entry.json", 1)
        val candidate = FakeDocumentFile.directory("case", "case")
            .add(uppercaseEntry)
            .add(exactEntry)
        val root = FakeDocumentFile.directory("root-case", "selected").add(candidate)

        val result = scanner(
            uppercaseEntry to """{"title":"Uppercase"}""",
            exactEntry to """{"title":"Exact"}"""
        ).scan(root).single()

        assertEquals("Exact", result.title)
    }

    @Test
    fun scan_returnsEmptyForUnreadableRoot() {
        val root = FakeDocumentFile.unreadableDirectory("unreadable", "selected")

        assertTrue(scanner().scan(root).isEmpty())
    }

    private fun scanner(
        vararg metadata: Pair<FakeDocumentFile, String>
    ): DocumentTreeBiliCacheScanner {
        val jsonByUri = metadata.associate { (file, json) -> file.uri.toString() to json }
        return DocumentTreeBiliCacheScanner(
            entryJsonReader = DocumentEntryJsonReader { entryFile ->
                jsonByUri[entryFile.uri.toString()].orEmpty()
            }
        )
    }
}
