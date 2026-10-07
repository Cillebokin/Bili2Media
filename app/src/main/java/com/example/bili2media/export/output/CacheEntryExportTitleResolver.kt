package com.example.bili2media.export.output

class CacheEntryExportTitleResolver {
    fun resolve(title: String, subtitle: String?): String {
        val normalizedTitle = title.trim()
        val normalizedSubtitle = subtitle?.trim().orEmpty()
        if (normalizedSubtitle.isEmpty() ||
            normalizedSubtitle.equals(normalizedTitle, ignoreCase = true)
        ) {
            return normalizedTitle
        }
        return "$normalizedTitle - $normalizedSubtitle"
    }
}
