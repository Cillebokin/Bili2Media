package com.example.bili2media.storage

import android.os.Environment
import java.io.File

object DefaultCacheDirectory {
    const val DISPLAY_PATH = "Download/Bili2Media/Input"

    @Suppress("DEPRECATION")
    fun file(): File {
        return File(Environment.getExternalStorageDirectory(), DISPLAY_PATH)
    }
}
