package com.example.bili2media.cache.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BiliCacheEntryFileSelectorTest {
    private val selector = BiliCacheEntryFileSelector()

    @Test
    fun select_prefersExactLowercaseEntryFile() {
        val files = listOf(
            NamedFile("ENTRY.JSON", "uppercase"),
            NamedFile("entry.json", "exact"),
            NamedFile("Entry.Json", "mixed")
        )

        assertEquals("exact", selector.select(files) { it.name }?.content)
    }

    @Test
    fun select_usesStableNameOrderWhenExactLowercaseFileIsMissing() {
        val files = listOf(
            NamedFile("Entry.Json", "mixed"),
            NamedFile("ENTRY.JSON", "uppercase")
        )

        assertEquals("uppercase", selector.select(files) { it.name }?.content)
        assertEquals("uppercase", selector.select(files.reversed()) { it.name }?.content)
    }

    @Test
    fun select_ignoresUnrelatedFiles() {
        val files = listOf(
            NamedFile("entry.json.bak", "backup"),
            NamedFile("video.m4s", "media")
        )

        assertNull(selector.select(files) { it.name })
    }

    private data class NamedFile(
        val name: String,
        val content: String
    )
}
