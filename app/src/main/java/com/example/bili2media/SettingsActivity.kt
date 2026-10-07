package com.example.bili2media

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Locale

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        bindLanguageSetting()
        fitContentBelowSystemBars(findViewById(R.id.main), findViewById(R.id.statusBarBackground))
    }

    private fun bindLanguageSetting() {
        val languageRow = findViewById<View>(R.id.rowLanguage)
        val languageValue = findViewById<TextView>(R.id.tvLanguageValue)
        updateLanguageValue(languageRow, languageValue)

        languageRow.setOnClickListener {
            val options = arrayOf(
                getString(R.string.language_simplified_chinese),
                getString(R.string.language_english)
            )
            AlertDialog.Builder(this)
                .setTitle(R.string.language)
                .setSingleChoiceItems(options, currentLanguageIndex()) { dialog, selectedIndex ->
                    dialog.dismiss()
                    val selectedTag = if (selectedIndex == ENGLISH_LANGUAGE_INDEX) {
                        ENGLISH_LANGUAGE_TAG
                    } else {
                        SIMPLIFIED_CHINESE_LANGUAGE_TAG
                    }
                    if (AppCompatDelegate.getApplicationLocales().toLanguageTags()
                            .equals(selectedTag, ignoreCase = true)
                    ) {
                        return@setSingleChoiceItems
                    }
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(selectedTag)
                    )
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun updateLanguageValue(languageRow: View, languageValue: TextView) {
        val currentLanguageString = if (currentLanguageIndex() == ENGLISH_LANGUAGE_INDEX) {
            R.string.language_current_english
        } else {
            R.string.language_current_chinese
        }
        languageValue.setText(currentLanguageString)
        languageRow.contentDescription = getString(
            R.string.language_setting_accessibility_description,
            getString(currentLanguageString)
        )
    }

    private fun currentLanguageIndex(): Int {
        val applicationLocale = AppCompatDelegate.getApplicationLocales().get(0)
        val language = (applicationLocale ?: resources.configuration.locales.get(0)).language
            .lowercase(Locale.ROOT)
        return if (language == ENGLISH_LANGUAGE_TAG) ENGLISH_LANGUAGE_INDEX else CHINESE_LANGUAGE_INDEX
    }

    private fun fitContentBelowSystemBars(rootView: View, statusBarBackground: View) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val statusBarColor = ContextCompat.getColor(this, R.color.bili2media_settings_background)
        val isDarkMode = (resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = statusBarColor
        WindowInsetsControllerCompat(window, rootView).apply {
            isAppearanceLightStatusBars = !isDarkMode
            isAppearanceLightNavigationBars = !isDarkMode
        }

        val initialLeft = rootView.paddingLeft
        val initialTop = rootView.paddingTop
        val initialRight = rootView.paddingRight
        val initialBottom = rootView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusBarBackground.setBackgroundColor(statusBarColor)
            val layoutParams = statusBarBackground.layoutParams
            if (layoutParams.height != systemBars.top) {
                layoutParams.height = systemBars.top
                statusBarBackground.layoutParams = layoutParams
            }
            view.setPadding(
                initialLeft + systemBars.left,
                initialTop,
                initialRight + systemBars.right,
                initialBottom + systemBars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(rootView)
    }

    private companion object {
        const val CHINESE_LANGUAGE_INDEX = 0
        const val ENGLISH_LANGUAGE_INDEX = 1
        const val SIMPLIFIED_CHINESE_LANGUAGE_TAG = "zh-CN"
        const val ENGLISH_LANGUAGE_TAG = "en"
    }
}
