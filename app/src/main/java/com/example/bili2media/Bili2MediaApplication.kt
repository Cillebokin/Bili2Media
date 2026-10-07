package com.example.bili2media

import android.app.Application

class Bili2MediaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.applyThemeMode(this)
    }
}
