package com.example.bili2media.export.m4a.output

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class M4aFileNameResolverTest {
    private val resolver = M4aFileNameResolver()

    @Test
    fun resolve_addsCanonicalSuffixAndIncrementsDuplicates() {
        assertEquals("Episode.m4a", resolver.resolve("Episode", emptySet(), 1L))
        assertEquals(
            "Episode (2).m4a",
            resolver.resolve(
                "Episode.m4a",
                setOf("episode.M4A", "Episode (1).m4a"),
                1L
            )
        )
    }

    @Test
    fun resolve_sanitizesNamesAndUsesFallback() {
        assertEquals(
            "Episode One.m4a",
            resolver.resolve(" Episode\\One? ", emptySet(), 1L)
        )
        assertEquals("Bili2Media-1.m4a", resolver.resolve(" ", emptySet(), 1L))
    }

    @Test
    fun resolve_limitsBasenameTo120UnicodeCodePoints() {
        val resolved = resolver.resolve("😀".repeat(121), emptySet(), 1L)
        val basename = resolved.removeSuffix(".m4a")

        assertEquals(120, basename.codePointCount(0, basename.length))
        assertFalse(basename.endsWith("\uD83D"))
    }
}
