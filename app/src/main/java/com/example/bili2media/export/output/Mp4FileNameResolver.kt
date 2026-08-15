package com.example.bili2media.export.output

import java.util.Locale

class Mp4FileNameResolver {
    fun resolve(
        title: String,
        existingNames: Set<String>,
        timestamp: Long
    ): String {
        val existing = existingNames.mapTo(mutableSetOf()) {
            it.lowercase(Locale.ROOT)
        }
        val sanitized = sanitize(title)
        val baseName = limitCodePoints(
            value = sanitized.ifBlank { "Bili2Media-$timestamp" },
            maximumCodePoints = MAX_BASENAME_CODE_POINTS
        )

        var candidate = "$baseName.mp4"
        var duplicateIndex = 1
        while (candidate.lowercase(Locale.ROOT) in existing) {
            candidate = "$baseName ($duplicateIndex).mp4"
            duplicateIndex++
        }
        return candidate
    }

    private fun sanitize(title: String): String {
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

        while (value.endsWith(MP4_SUFFIX, ignoreCase = true)) {
            value = value.dropLast(MP4_SUFFIX.length).trim()
        }
        return value
    }

    private fun limitCodePoints(value: String, maximumCodePoints: Int): String {
        val codePointCount = value.codePointCount(0, value.length)
        if (codePointCount <= maximumCodePoints) {
            return value
        }
        val endIndex = value.offsetByCodePoints(0, maximumCodePoints)
        return value.substring(0, endIndex).trim()
    }

    private companion object {
        const val MP4_SUFFIX = ".mp4"
        const val MAX_BASENAME_CODE_POINTS = 120
        val INVALID_FILENAME_CHARACTERS = setOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
        val WHITESPACE = Regex("\\s+")
    }
}
