package com.example.bili2media.storage

import android.net.Uri

sealed interface CacheRootSelection {
    data object Default : CacheRootSelection

    data class Tree(
        val uri: Uri
    ) : CacheRootSelection
}
