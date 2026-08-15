package com.example.bili2media.ui.image

import android.widget.ImageView
import androidx.core.net.toUri
import coil.load
import com.example.bili2media.R
import com.example.bili2media.cache.model.CoverSource

class CoilCoverImageLoader : CoverImageLoader {
    override fun load(target: ImageView, source: CoverSource?) {
        val data = when (source) {
            is CoverSource.Local -> source.uri.toUri()
            is CoverSource.Remote -> source.url
            null -> null
        }

        target.load(data) {
            placeholder(R.drawable.ic_cover_placeholder)
            error(R.drawable.ic_cover_placeholder)
            fallback(R.drawable.ic_cover_placeholder)
            crossfade(true)
        }
    }
}
