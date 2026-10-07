package com.example.bili2media.export.output

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore

class MediaStoreMp4OutputStore(
    context: Context,
    private val fileNameResolver: Mp4FileNameResolver = Mp4FileNameResolver(),
    private val timestampProvider: () -> Long = System::currentTimeMillis
) : Mp4OutputStore {
    private val contentResolver: ContentResolver = context.applicationContext.contentResolver

    fun suggestDuplicateName(title: String): String? {
        return synchronized(CREATE_LOCK) {
            fileNameResolver.suggestDuplicateName(
                title = title,
                existingNames = queryExistingNames(),
                timestamp = timestampProvider()
            )
        }
    }

    override fun create(title: String): Mp4PendingOutput {
        return synchronized(CREATE_LOCK) {
            val displayName = fileNameResolver.resolve(
                title = title,
                existingNames = queryExistingNames(),
                timestamp = timestampProvider()
            )
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, MP4_MIME_TYPE)
                put(MediaStore.Downloads.RELATIVE_PATH, OUTPUT_RELATIVE_PATH)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            ) ?: error("MediaStore did not create an MP4 output")
            Mp4PendingOutput(
                uri = uri.toString(),
                displayName = displayName
            )
        }
    }

    override fun commit(output: Mp4PendingOutput) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.IS_PENDING, 0)
        }
        val updated = contentResolver.update(
            Uri.parse(output.uri),
            values,
            null,
            null
        )
        check(updated > 0) { "Pending MP4 output no longer exists" }
    }

    override fun abandon(output: Mp4PendingOutput) {
        contentResolver.delete(Uri.parse(output.uri), null, null)
    }

    private fun queryExistingNames(): Set<String> {
        val names = mutableSetOf<String>()
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} = ? OR " +
            "${MediaStore.Downloads.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(
            OUTPUT_RELATIVE_PATH,
            "$OUTPUT_RELATIVE_PATH/"
        )
        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
        }
        contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads.DISPLAY_NAME),
            queryArgs,
            null
        )?.use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                cursor.getString(nameColumn)?.let(names::add)
            }
        }
        return names
    }

    companion object {
        const val OUTPUT_RELATIVE_PATH = "Download/Bili2Media/Output"
        private const val MP4_MIME_TYPE = "video/mp4"
        private val CREATE_LOCK = Any()
    }
}
