package com.example.bili2media.export.m4a.output

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import com.example.bili2media.export.output.SelectedOutputDirectory
import com.example.bili2media.storage.OutputDirectoryStore

class MediaStoreM4aOutputStore(
    context: Context,
    private val fileNameResolver: M4aFileNameResolver = M4aFileNameResolver(),
    private val timestampProvider: () -> Long = System::currentTimeMillis
) : M4aOutputStore {
    private val contentResolver: ContentResolver = context.applicationContext.contentResolver
    private val selectedOutputDirectory = SelectedOutputDirectory(context)

    fun suggestDuplicateName(title: String): String? {
        return synchronized(CREATE_LOCK) {
            fileNameResolver.suggestDuplicateName(
                title = title,
                existingNames = queryExistingNames(),
                timestamp = timestampProvider()
            )
        }
    }

    override fun create(title: String): M4aPendingOutput {
        return synchronized(CREATE_LOCK) {
            val displayName = fileNameResolver.resolve(
                title,
                queryExistingNames(),
                timestampProvider()
            )
            selectedOutputDirectory.createFile(displayName, M4A_MIME_TYPE)?.let { uri ->
                return@synchronized M4aPendingOutput(
                    uri.toString(),
                    displayName,
                    isUserSelectedDirectory = true
                )
            }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, M4A_MIME_TYPE)
                put(MediaStore.Downloads.RELATIVE_PATH, OUTPUT_RELATIVE_PATH)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            ) ?: error("MediaStore did not create an M4A output")
            M4aPendingOutput(uri.toString(), displayName)
        }
    }

    override fun commit(output: M4aPendingOutput) {
        if (output.isUserSelectedDirectory) return

        val values = ContentValues().apply {
            put(MediaStore.Downloads.IS_PENDING, 0)
        }
        val updated = contentResolver.update(Uri.parse(output.uri), values, null, null)
        check(updated > 0) { "Pending M4A output no longer exists" }
    }

    override fun abandon(output: M4aPendingOutput) {
        if (output.isUserSelectedDirectory) {
            selectedOutputDirectory.deleteFile(Uri.parse(output.uri))
        } else {
            contentResolver.delete(Uri.parse(output.uri), null, null)
        }
    }

    private fun queryExistingNames(): Set<String> {
        selectedOutputDirectory.existingFileNames()?.let { return it }

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
            while (cursor.moveToNext()) {
                cursor.getString(column)?.let(names::add)
            }
        }
        return names
    }

    companion object {
        const val OUTPUT_RELATIVE_PATH = OutputDirectoryStore.DEFAULT_RELATIVE_PATH
        private const val M4A_MIME_TYPE = "audio/mp4"
        private val CREATE_LOCK = Any()
    }
}
