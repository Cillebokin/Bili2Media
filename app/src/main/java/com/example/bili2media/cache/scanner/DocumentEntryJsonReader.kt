package com.example.bili2media.cache.scanner

import android.content.ContentResolver
import androidx.documentfile.provider.DocumentFile

fun interface DocumentEntryJsonReader {
    fun read(entryFile: DocumentFile): String
}

internal class ContentResolverDocumentEntryJsonReader(
    private val contentResolver: ContentResolver
) : DocumentEntryJsonReader {
    override fun read(entryFile: DocumentFile): String {
        return contentResolver.openInputStream(entryFile.uri)?.use { input ->
            input.bufferedReader().readText()
        }.orEmpty()
    }
}
