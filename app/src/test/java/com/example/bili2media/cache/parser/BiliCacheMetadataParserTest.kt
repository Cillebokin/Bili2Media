package com.example.bili2media.cache.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BiliCacheMetadataParserTest {
    private val parser = BiliCacheMetadataParser()

    @Test
    fun parse_readsCommonEntryFields() {
        val metadata = parser.parse(
            """{
                "title":"Example Video",
                "avid":123,
                "page_data":{"part":"Part 1","cid":456}
            }""".trimIndent()
        )

        assertEquals("Example Video", metadata.title)
        assertEquals("Part 1", metadata.subtitle)
        assertEquals(123L, metadata.avid)
        assertEquals(456L, metadata.cid)
    }

    @Test
    fun parse_usesEpisodeTitleAndIdsWhenPageDataIsAbsent() {
        val metadata = parser.parse(
            """{
                "title":"Episode Cache",
                "ep":{"index_title":"Episode 7","avid":700,"cid":701}
            }""".trimIndent()
        )

        assertEquals("Episode Cache", metadata.title)
        assertEquals("Episode 7", metadata.subtitle)
        assertEquals(700L, metadata.avid)
        assertEquals(701L, metadata.cid)
    }

    @Test
    fun parse_usesEpisodeIndexWhenIndexTitleIsBlank() {
        val metadata = parser.parse(
            """{
                "ep":{"index_title":"  ","index":"3"}
            }""".trimIndent()
        )

        assertEquals("3", metadata.subtitle)
    }

    @Test
    fun parse_returnsNullForMissingOrNonPositiveOptionalValues() {
        val metadata = parser.parse(
            """{
                "title":"  ",
                "avid":0,
                "cid":-1,
                "page_data":{}
            }""".trimIndent()
        )

        assertNull(metadata.title)
        assertNull(metadata.subtitle)
        assertNull(metadata.avid)
        assertNull(metadata.cid)
    }

    @Test
    fun parse_readsTopLevelCoverBeforeNestedCovers() {
        val metadata = parser.parse(
            """{
                "cover":"https://i0.hdslb.com/root.jpg",
                "page_data":{"cover":"https://i0.hdslb.com/page.jpg"},
                "ep":{"cover":"https://i0.hdslb.com/episode.jpg"}
            }""".trimIndent()
        )

        assertEquals("https://i0.hdslb.com/root.jpg", metadata.coverUrl)
    }

    @Test
    fun parse_readsPageDataCoverWhenTopLevelCoverIsBlank() {
        val metadata = parser.parse(
            """{
                "cover":"  ",
                "page_data":{"cover":"//i0.hdslb.com/page.jpg"},
                "ep":{"cover":"https://i0.hdslb.com/episode.jpg"}
            }""".trimIndent()
        )

        assertEquals("//i0.hdslb.com/page.jpg", metadata.coverUrl)
    }

    @Test
    fun parse_readsEpisodeCoverWhenOtherCoverFieldsAreMissing() {
        val metadata = parser.parse(
            """{
                "ep":{"cover":"http://i0.hdslb.com/episode.jpg"}
            }""".trimIndent()
        )

        assertEquals("http://i0.hdslb.com/episode.jpg", metadata.coverUrl)
    }
}
