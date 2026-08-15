package com.example.bili2media.ui.image

import android.widget.ImageView
import com.example.bili2media.cache.model.CoverSource

fun interface CoverImageLoader {
    fun load(target: ImageView, source: CoverSource?)
}
