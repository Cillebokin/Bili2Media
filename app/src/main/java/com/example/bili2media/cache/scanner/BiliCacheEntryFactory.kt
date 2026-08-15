package com.example.bili2media.cache.scanner

import com.example.bili2media.cache.cover.BiliCacheCoverResolver
import com.example.bili2media.cache.model.BiliCacheEntry
import com.example.bili2media.cache.model.BiliCacheStatus
import com.example.bili2media.cache.parser.BiliCacheMetadataParser

class BiliCacheEntryFactory(
    private val metadataParser: BiliCacheMetadataParser = BiliCacheMetadataParser(),
    private val coverResolver: BiliCacheCoverResolver = BiliCacheCoverResolver()
) {
    fun create(
        fallbackName: String,
        relativePath: String,
        jsonText: String,
        mediaFiles: List<BiliCacheMedia>,
        localCoverUri: String? = null
    ): BiliCacheEntry {
        val metadataResult = runCatching { metadataParser.parse(jsonText) }
        val metadata = metadataResult.getOrNull()
        val status = when {
            metadataResult.isFailure -> BiliCacheStatus.METADATA_ERROR
            mediaFiles.isEmpty() -> BiliCacheStatus.NO_MEDIA
            else -> BiliCacheStatus.AVAILABLE
        }

        return BiliCacheEntry(
            id = relativePath,
            title = metadata?.title ?: fallbackName,
            subtitle = metadata?.subtitle,
            avid = metadata?.avid,
            cid = metadata?.cid,
            relativePath = relativePath,
            mediaFileCount = mediaFiles.size,
            totalBytes = mediaFiles.sumOf { it.size.coerceAtLeast(0L) },
            status = status,
            coverSource = coverResolver.resolve(localCoverUri, metadata?.coverUrl)
        )
    }
}
