package com.example.bili2media.cache.parser

import com.example.bili2media.cache.model.BiliCacheMetadata
import org.json.JSONObject

class BiliCacheMetadataParser {
    fun parse(jsonText: String): BiliCacheMetadata {
        val root = JSONObject(jsonText)
        val pageData = root.optJSONObject(KEY_PAGE_DATA)
        val episode = root.optJSONObject(KEY_EPISODE)

        return BiliCacheMetadata(
            title = root.optNonBlankText(KEY_TITLE),
            subtitle = pageData?.optNonBlankText(KEY_PART)
                ?: episode?.optNonBlankText(KEY_INDEX_TITLE)
                ?: episode?.optNonBlankText(KEY_INDEX),
            avid = root.optPositiveLong(KEY_AVID)
                ?: episode?.optPositiveLong(KEY_AVID),
            cid = root.optPositiveLong(KEY_CID)
                ?: pageData?.optPositiveLong(KEY_CID)
                ?: episode?.optPositiveLong(KEY_CID),
            coverUrl = root.optNonBlankText(KEY_COVER)
                ?: pageData?.optNonBlankText(KEY_COVER)
                ?: episode?.optNonBlankText(KEY_COVER)
        )
    }

    private fun JSONObject.optNonBlankText(key: String): String? {
        val value = opt(key)
        if (value == null || value === JSONObject.NULL) {
            return null
        }
        return value.toString().trim().takeIf { it.isNotEmpty() }
    }

    private fun JSONObject.optPositiveLong(key: String): Long? {
        val value = opt(key)
        if (value == null || value === JSONObject.NULL) {
            return null
        }
        val number = when (value) {
            is Number -> value.toLong()
            is String -> value.trim().toLongOrNull()
            else -> null
        }
        return number?.takeIf { it > 0L }
    }

    private companion object {
        const val KEY_TITLE = "title"
        const val KEY_PAGE_DATA = "page_data"
        const val KEY_EPISODE = "ep"
        const val KEY_PART = "part"
        const val KEY_INDEX_TITLE = "index_title"
        const val KEY_INDEX = "index"
        const val KEY_AVID = "avid"
        const val KEY_CID = "cid"
        const val KEY_COVER = "cover"
    }
}
