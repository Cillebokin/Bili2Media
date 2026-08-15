package com.example.bili2media.export.output

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class Mp4FileNameResolverTest {
    private val resolver = Mp4FileNameResolver()

    @Test
    fun resolve_addsCanonicalSuffixAndIncrementsDuplicates() {
        assertEquals(
            "Episode.mp4",
            resolver.resolve("Episode", emptySet(), 1L)
        )
        assertEquals(
            "Episode (2).mp4",
            resolver.resolve(
                title = "Episode",
                existingNames = setOf("Episode.mp4", "Episode (1).mp4"),
                timestamp = 1L
            )
        )
    }

    @Test
    fun resolve_sanitizesInvalidCharactersAndWhitespace() {
        assertEquals(
            "Episode One Final.mp4",
            resolver.resolve(
                title = "  Episode\\One:Final?  ",
                existingNames = emptySet(),
                timestamp = 1L
            )
        )
        assertEquals(
            "Line Break.mp4",
            resolver.resolve(
                title = "Line\u0000\nBreak",
                existingNames = emptySet(),
                timestamp = 1L
            )
        )
    }

    @Test
    fun resolve_removesExistingMp4SuffixCaseInsensitively() {
        assertEquals(
            "Episode.mp4",
            resolver.resolve("Episode.MP4", emptySet(), 1L)
        )
        assertEquals(
            "Episode.mp4",
            resolver.resolve("Episode.mp4.mp4", emptySet(), 1L)
        )
    }

    @Test
    fun resolve_usesTimestampFallbackForBlankTitle() {
        assertEquals(
            "Bili2Media-1.mp4",
            resolver.resolve("  ", emptySet(), 1L)
        )
    }

    @Test
    fun resolve_limitsBasenameTo120UnicodeCodePoints() {
        val title = "😀".repeat(121)

        val resolved = resolver.resolve(title, emptySet(), 1L)
        val basename = resolved.removeSuffix(".mp4")

        assertEquals(120, basename.codePointCount(0, basename.length))
        assertFalse(basename.endsWith("\uD83D"))
    }

    @Test
    fun resolve_treatsExistingNamesCaseInsensitively() {
        assertEquals(
            "Episode (1).mp4",
            resolver.resolve("Episode", setOf("episode.MP4"), 1L)
        )
    }
}
