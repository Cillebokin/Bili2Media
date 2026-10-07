package com.example.bili2media

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object AppSettings {
    const val DEFAULT_MP3_BITRATE_KBPS = 192
    val MP3_BITRATE_OPTIONS_KBPS = listOf(128, 192, 256, 320)

    private const val PREFERENCES_NAME = "app_settings"
    private const val KEY_DARK_MODE_ENABLED = "dark_mode_enabled"
    private const val KEY_MP3_BITRATE_KBPS = "mp3_bitrate_kbps"

    fun isDarkModeEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK_MODE_ENABLED, false)

    fun setDarkModeEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK_MODE_ENABLED, enabled)
            .apply()
        AppCompatDelegate.setDefaultNightMode(
            if (enabled) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    fun applyThemeMode(context: Context) {
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkModeEnabled(context)) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
        )
    }

    fun getMp3BitrateKbps(context: Context): Int {
        val storedValue = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_MP3_BITRATE_KBPS, DEFAULT_MP3_BITRATE_KBPS)
        return storedValue.takeIf { it in MP3_BITRATE_OPTIONS_KBPS }
            ?: DEFAULT_MP3_BITRATE_KBPS
    }

    fun setMp3BitrateKbps(context: Context, bitrateKbps: Int) {
        require(bitrateKbps in MP3_BITRATE_OPTIONS_KBPS)
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_MP3_BITRATE_KBPS, bitrateKbps)
            .apply()
    }
}
