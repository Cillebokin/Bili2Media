package com.example.bili2media.export.output

class Mp4FileNameResolver(
    private val resolver: MediaFileNameResolver = MediaFileNameResolver()
) {
    fun resolve(
        title: String,
        existingNames: Set<String>,
        timestamp: Long
    ): String {
        return resolver.resolve(
            title = title,
            existingNames = existingNames,
            timestamp = timestamp,
            extension = "mp4",
            fallbackPrefix = "Bili2Media"
        )
    }
}
