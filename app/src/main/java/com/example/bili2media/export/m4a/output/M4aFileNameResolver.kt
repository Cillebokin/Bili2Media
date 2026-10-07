package com.example.bili2media.export.m4a.output

import com.example.bili2media.export.output.MediaFileNameResolver

class M4aFileNameResolver(
    private val resolver: MediaFileNameResolver = MediaFileNameResolver()
) {
    fun suggestDuplicateName(
        title: String,
        existingNames: Set<String>,
        timestamp: Long
    ): String? {
        return resolver.suggestDuplicateName(
            title = title,
            existingNames = existingNames,
            timestamp = timestamp,
            extension = "m4a",
            fallbackPrefix = "Bili2Media"
        )
    }

    fun resolve(
        title: String,
        existingNames: Set<String>,
        timestamp: Long
    ): String {
        return resolver.resolve(
            title = title,
            existingNames = existingNames,
            timestamp = timestamp,
            extension = "m4a",
            fallbackPrefix = "Bili2Media"
        )
    }
}
