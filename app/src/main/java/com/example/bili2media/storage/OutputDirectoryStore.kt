package com.example.bili2media.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile

class OutputDirectoryStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun currentTreeUri(): Uri? {
        val savedUri = preferences.getString(KEY_TREE_URI, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val uri = runCatching { Uri.parse(savedUri) }.getOrNull()
        if (uri?.scheme != CONTENT_SCHEME) {
            useDefault()
            return null
        }
        return uri
    }

    fun displayName(): String? = currentTreeUri()
        ?.let { DocumentFile.fromTreeUri(appContext, it)?.name }

    fun saveTree(uri: Uri): Boolean {
        if (uri.scheme != CONTENT_SCHEME) return false
        val accessPersisted = runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }.isSuccess
        if (!accessPersisted) return false

        preferences.edit {
            putString(KEY_TREE_URI, uri.toString())
        }
        return true
    }

    fun useDefault() {
        preferences.edit { remove(KEY_TREE_URI) }
    }

    companion object {
        const val DEFAULT_RELATIVE_PATH = "Download/Bili2Media/Output"

        private const val PREFERENCES_NAME = "output_directory"
        private const val KEY_TREE_URI = "tree_uri"
        private const val CONTENT_SCHEME = "content"
    }
}
