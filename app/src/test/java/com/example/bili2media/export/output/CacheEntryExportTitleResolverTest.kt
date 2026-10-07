package com.example.bili2media.export.output

import org.junit.Assert.assertEquals
import org.junit.Test

class CacheEntryExportTitleResolverTest {
    private val resolver = CacheEntryExportTitleResolver()

    @Test
    fun resolve_appendsDistinctSubtitleForCollectionEntry() {
        assertEquals(
            "Android Course - Chapter 02 Compose",
            resolver.resolve(
                title = "Android Course",
                subtitle = "Chapter 02 Compose"
            )
        )
    }

    @Test
    fun resolve_keepsTitleWhenSubtitleIsMissing() {
        assertEquals("Standalone Video", resolver.resolve("Standalone Video", null))
        assertEquals("Standalone Video", resolver.resolve("Standalone Video", "  "))
    }

    @Test
    fun resolve_doesNotDuplicateEquivalentSubtitle() {
        assertEquals(
            "Standalone Video",
            resolver.resolve("Standalone Video", " standalone video ")
        )
    }
}
