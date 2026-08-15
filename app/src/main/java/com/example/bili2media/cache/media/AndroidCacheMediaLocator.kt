package com.example.bili2media.cache.media

import android.content.Context
import com.example.bili2media.cache.model.CacheEntryLocation
import com.example.bili2media.cache.model.CacheMediaFile

class AndroidCacheMediaLocator(
    context: Context,
    private val fileLocator: CacheMediaLocator = FileCacheMediaLocator(),
    private val documentLocator: CacheMediaLocator = DocumentCacheMediaLocator(
        context.applicationContext
    )
) : CacheMediaLocator {
    override fun locate(location: CacheEntryLocation): List<CacheMediaFile> {
        return when (location) {
            is CacheEntryLocation.FileDirectory -> fileLocator.locate(location)
            is CacheEntryLocation.DocumentDirectory -> documentLocator.locate(location)
        }
    }
}
