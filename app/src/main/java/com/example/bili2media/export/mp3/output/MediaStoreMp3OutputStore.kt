package com.example.bili2media.export.mp3.output

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import com.example.bili2media.export.output.MediaStoreMp4OutputStore

class MediaStoreMp3OutputStore(
    context: Context,
    private val fileNameResolver: Mp3FileNameResolver = Mp3FileNameResolver(),
    private val timestampProvider: () -> Long = System::currentTimeMillis
) : Mp3OutputStore {
    private val contentResolver: ContentResolver = context.applicationContext.contentResolver

    fun suggestDuplicateName(title: String): String? = synchronized(CREATE_LOCK) {
        fileNameResolver.suggestDuplicateName(title, queryExistingNames(), timestampProvider())
    }

    override fun create(title: String): Mp3PendingOutput = synchronized(CREATE_LOCK) {
        val displayName = fileNameResolver.resolve(title, queryExistingNames(), timestampProvider())
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, MP3_MIME_TYPE)
            put(MediaStore.Downloads.RELATIVE_PATH, OUTPUT_RELATIVE_PATH)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore did not create an MP3 output")
        Mp3PendingOutput(uri.toString(), displayName)
    }

    override fun commit(output: Mp3PendingOutput) {
        val updated = contentResolver.update(
            Uri.parse(output.uri),
            ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
            null,
            null
        )
        check(updated > 0) { "Pending MP3 output no longer exists" }
    }

    override fun abandon(output: Mp3PendingOutput) {
        contentResolver.delete(Uri.parse(output.uri), null, null)
    }

    private fun queryExistingNames(): Set<String> {
        val names = mutableSetOf<String>()
        val queryArgs = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Downloads.RELATIVE_PATH} = ? OR " +
                    "${MediaStore.Downloads.RELATIVE_PATH} = ?"
            )
            putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(OUTPUT_RELATIVE_PATH, "$OUTPUT_RELATIVE_PATH/")
            )
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
        }
        contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads.DISPLAY_NAME),
            queryArgs,
            null
        )?.use { cursor ->
            val column = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            while (cursor.moveToNext()) cursor.getString(column)?.let(names::add)
        }
        return names
    }

    companion object {
        const val OUTPUT_RELATIVE_PATH = MediaStoreMp4OutputStore.OUTPUT_RELATIVE_PATH
        private const val MP3_MIME_TYPE = "audio/mpeg"
        private val CREATE_LOCK = Any()
    }
}
