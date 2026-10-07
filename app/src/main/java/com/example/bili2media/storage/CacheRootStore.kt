package com.example.bili2media.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri

class CacheRootStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun current(): CacheRootSelection {
        val savedUri = preferences.getString(KEY_TREE_URI, null)
            ?.takeIf { it.isNotBlank() }
            ?: return CacheRootSelection.Default
        val uri = runCatching { savedUri.toUri() }.getOrNull()
        if (uri?.scheme != CONTENT_SCHEME) {
            clearTree()
            return CacheRootSelection.Default
        }
        return CacheRootSelection.Tree(uri)
    }

    fun useDefault() {
        clearTree()
    }

    fun saveTree(uri: Uri): Boolean {
        if (uri.scheme != CONTENT_SCHEME) return false
        val accessPersisted = runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }.isSuccess
        if (!accessPersisted) return false

        preferences.edit {
            putString(KEY_TREE_URI, uri.toString())
        }
        return true
    }

    fun clearTree() {
        preferences.edit {
            remove(KEY_TREE_URI)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "cache_root"
        const val KEY_TREE_URI = "tree_uri"
        const val CONTENT_SCHEME = "content"
    }
}
