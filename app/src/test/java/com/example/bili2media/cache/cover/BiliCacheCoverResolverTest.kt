package com.example.bili2media.cache.cover

import com.example.bili2media.cache.model.CoverSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BiliCacheCoverResolverTest {
    private val resolver = BiliCacheCoverResolver()

    @Test
    fun resolve_prefersTrimmedLocalCoverOverRemoteCover() {
        assertEquals(
            CoverSource.Local("content://local/cover"),
            resolver.resolve(
                localCoverUri = "  content://local/cover  ",
                remoteCoverUrl = "http://i0.hdslb.com/remote.jpg"
            )
        )
    }

    @Test
    fun resolve_upgradesHttpRemoteCoverToHttps() {
        assertEquals(
            CoverSource.Remote("https://i0.hdslb.com/remote.jpg"),
            resolver.resolve(null, "http://i0.hdslb.com/remote.jpg")
        )
    }

    @Test
    fun resolve_normalizesProtocolRelativeRemoteCoverToHttps() {
        assertEquals(
            CoverSource.Remote("https://i0.hdslb.com/remote.jpg"),
            resolver.resolve(null, "//i0.hdslb.com/remote.jpg")
        )
    }

    @Test
    fun resolve_preservesTrimmedHttpsRemoteCover() {
        assertEquals(
            CoverSource.Remote("https://i0.hdslb.com/remote.jpg"),
            resolver.resolve(null, "  https://i0.hdslb.com/remote.jpg  ")
        )
    }

    @Test
    fun resolve_rejectsBlankAndNonHttpRemoteCovers() {
        assertNull(resolver.resolve(null, "  "))
        assertNull(resolver.resolve(null, "ftp://example.com/cover.jpg"))
        assertNull(resolver.resolve(null, "content://example/cover.jpg"))
    }

    @Test
    fun isLocalCoverFileName_acceptsOnlyExplicitNamesIgnoringCase() {
        listOf("cover.jpg", "COVER.JPEG", "Cover.Png", "cover.WEBP").forEach { fileName ->
            assertTrue(fileName, resolver.isLocalCoverFileName(fileName))
        }

        listOf(
            "poster.jpg",
            "cover.gif",
            "video-cover.jpg",
            "cover.jpg.tmp",
            "cover"
        ).forEach { fileName ->
            assertFalse(fileName, resolver.isLocalCoverFileName(fileName))
        }
    }
}
