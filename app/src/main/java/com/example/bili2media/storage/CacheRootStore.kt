package com.example.bili2media.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri

class CacheRootStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

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

    fun saveTree(uri: Uri) {
        preferences.edit {
            putString(KEY_TREE_URI, uri.toString())
        }
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
