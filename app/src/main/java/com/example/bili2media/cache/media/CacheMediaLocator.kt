package com.example.bili2media.cache.media

import com.example.bili2media.cache.model.CacheEntryLocation
import com.example.bili2media.cache.model.CacheMediaFile
import java.util.Locale

fun interface CacheMediaLocator {
    fun locate(location: CacheEntryLocation): List<CacheMediaFile>
}

internal fun isExportInputFileName(fileName: String): Boolean {
    return fileName.substringAfterLast('.', missingDelimiterValue = "")
        .lowercase(Locale.ROOT) in EXPORT_INPUT_EXTENSIONS
}

private val EXPORT_INPUT_EXTENSIONS = setOf("m4s", "mp4", "m4a")
