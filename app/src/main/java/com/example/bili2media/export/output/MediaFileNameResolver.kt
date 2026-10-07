package com.example.bili2media.export.output

import java.util.Locale

class MediaFileNameResolver {
    fun suggestDuplicateName(
        title: String,
        existingNames: Set<String>,
        timestamp: Long,
        extension: String,
        fallbackPrefix: String
    ): String? {
        val defaultName = resolve(
            title = title,
            existingNames = emptySet(),
            timestamp = timestamp,
            extension = extension,
            fallbackPrefix = fallbackPrefix
        )
        if (existingNames.none { it.equals(defaultName, ignoreCase = true) }) {
            return null
        }
        return resolve(title, existingNames, timestamp, extension, fallbackPrefix)
    }

    fun resolve(
        title: String,
        existingNames: Set<String>,
        timestamp: Long,
        extension: String,
        fallbackPrefix: String
    ): String {
        val normalizedExtension = extension.trim().trimStart('.').lowercase(Locale.ROOT)
        require(normalizedExtension.isNotEmpty()) { "File extension is required" }
        val suffix = ".$normalizedExtension"
        val existing = existingNames.mapTo(mutableSetOf()) { it.lowercase(Locale.ROOT) }
        val sanitized = sanitize(title, suffix)
        val baseName = limitCodePoints(
            sanitized.ifBlank { "$fallbackPrefix-$timestamp" },
            MAX_BASENAME_CODE_POINTS
        )

        var candidate = "$baseName$suffix"
        var duplicateIndex = 1
        while (candidate.lowercase(Locale.ROOT) in existing) {
            candidate = "$baseName ($duplicateIndex)$suffix"
            duplicateIndex++
        }
        return candidate
    }

    private fun sanitize(title: String, suffix: String): String {
        var value = buildString(title.length) {
            title.forEach { character ->
                append(
                    when {
                        character in INVALID_FILENAME_CHARACTERS -> ' '
                        Character.isISOControl(character) -> ' '
                        else -> character
                    }
                )
            }
        }.replace(WHITESPACE, " ").trim()

        while (value.endsWith(suffix, ignoreCase = true)) {
            value = value.dropLast(suffix.length).trim()
        }
        return value
    }

    private fun limitCodePoints(value: String, maximumCodePoints: Int): String {
        if (value.codePointCount(0, value.length) <= maximumCodePoints) {
            return value
        }
        return value.substring(0, value.offsetByCodePoints(0, maximumCodePoints)).trim()
    }

    private companion object {
        const val MAX_BASENAME_CODE_POINTS = 120
        val INVALID_FILENAME_CHARACTERS = setOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
        val WHITESPACE = Regex("\\s+")
    }
}
