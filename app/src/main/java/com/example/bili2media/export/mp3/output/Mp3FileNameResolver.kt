package com.example.bili2media.export.mp3.output

import com.example.bili2media.export.output.MediaFileNameResolver

class Mp3FileNameResolver(
    private val resolver: MediaFileNameResolver = MediaFileNameResolver()
) {
    fun suggestDuplicateName(title: String, existingNames: Set<String>, timestamp: Long): String? =
        resolver.suggestDuplicateName(title, existingNames, timestamp, "mp3", "Bili2Media")

    fun resolve(title: String, existingNames: Set<String>, timestamp: Long): String =
        resolver.resolve(title, existingNames, timestamp, "mp3", "Bili2Media")
}
