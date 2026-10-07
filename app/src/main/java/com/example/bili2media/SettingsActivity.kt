package com.example.bili2media

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import com.example.bili2media.storage.CacheRootSelection
import com.example.bili2media.storage.CacheRootStore
import com.example.bili2media.storage.DefaultCacheDirectory
import com.example.bili2media.storage.OutputDirectoryStore
import java.util.Locale

class SettingsActivity : AppCompatActivity() {
    private val cacheRootStore by lazy { CacheRootStore(this) }
    private val outputDirectoryStore by lazy { OutputDirectoryStore(this) }

    private val inputDirectoryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            if (!cacheRootStore.saveTree(uri)) {
                showDirectoryPermissionError()
            }
            updateDirectoryValues()
        }
    }

    private val outputDirectoryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            if (!outputDirectoryStore.saveTree(uri)) {
                showDirectoryPermissionError()
            }
            updateDirectoryValues()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        bindLanguageSetting()
        bindDarkModeSetting()
        bindDirectorySettings()
        bindMp3BitrateSetting()
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

    private fun bindDarkModeSetting() {
        val darkModeSwitch = findViewById<SwitchCompat>(R.id.switchDarkMode)
        darkModeSwitch.isChecked = AppSettings.isDarkModeEnabled(this)
        darkModeSwitch.setOnCheckedChangeListener { _, enabled ->
            AppSettings.setDarkModeEnabled(this, enabled)
        }
    }

    private fun bindMp3BitrateSetting() {
        val bitrateRow = findViewById<View>(R.id.rowMp3Bitrate)
        val bitrateValue = findViewById<TextView>(R.id.tvMp3BitrateValue)
        updateMp3BitrateValue(bitrateRow, bitrateValue)

        bitrateRow.setOnClickListener {
            val bitrates = AppSettings.MP3_BITRATE_OPTIONS_KBPS
            val options = bitrates.map { bitrate ->
                getString(R.string.mp3_bitrate_value_format, bitrate)
            }.toTypedArray()
            val selectedIndex = bitrates.indexOf(AppSettings.getMp3BitrateKbps(this))
            AlertDialog.Builder(this)
                .setTitle(R.string.mp3_bitrate)
                .setSingleChoiceItems(options, selectedIndex) { dialog, index ->
                    dialog.dismiss()
                    val selectedBitrate = bitrates.getOrNull(index) ?: return@setSingleChoiceItems
                    AppSettings.setMp3BitrateKbps(this, selectedBitrate)
                    updateMp3BitrateValue(bitrateRow, bitrateValue)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun bindDirectorySettings() {
        val inputDirectoryRow = findViewById<View>(R.id.rowInputDirectory)
        val inputDirectoryValue = findViewById<TextView>(R.id.tvInputDirectoryValue)
        val outputDirectoryRow = findViewById<View>(R.id.rowOutputDirectory)
        val outputDirectoryValue = findViewById<TextView>(R.id.tvOutputDirectoryValue)

        updateDirectoryValues()

        inputDirectoryRow.setOnClickListener {
            val selection = cacheRootStore.current()
            val selectedTree = selection as? CacheRootSelection.Tree
            showDirectoryActions(
                titleRes = R.string.input_directory_label,
                hasCustomDirectory = selectedTree != null,
                chooseDirectory = {
                    inputDirectoryPickerLauncher.launch(selectedTree?.uri)
                },
                useDefaultDirectory = {
                    cacheRootStore.useDefault()
                    updateDirectoryValues()
                }
            )
        }

        outputDirectoryRow.setOnClickListener {
            val selectedTree = outputDirectoryStore.currentTreeUri()
            showDirectoryActions(
                titleRes = R.string.output_directory_label,
                hasCustomDirectory = selectedTree != null,
                chooseDirectory = {
                    outputDirectoryPickerLauncher.launch(selectedTree)
                },
                useDefaultDirectory = {
                    outputDirectoryStore.useDefault()
                    updateDirectoryValues()
                }
            )
        }

        inputDirectoryValue.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        outputDirectoryValue.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun showDirectoryActions(
        titleRes: Int,
        hasCustomDirectory: Boolean,
        chooseDirectory: () -> Unit,
        useDefaultDirectory: () -> Unit
    ) {
        val options = if (hasCustomDirectory) {
            arrayOf(
                getString(R.string.directory_choose_other),
                getString(R.string.directory_use_default)
            )
        } else {
            arrayOf(getString(R.string.directory_choose_folder))
        }
        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setItems(options) { _, selectedIndex ->
                if (hasCustomDirectory && selectedIndex == 1) {
                    useDefaultDirectory()
                } else {
                    chooseDirectory()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateDirectoryValues() {
        val inputDirectoryRow = findViewById<View>(R.id.rowInputDirectory)
        val inputDirectoryValue = findViewById<TextView>(R.id.tvInputDirectoryValue)
        val inputSelection = cacheRootStore.current()
        val inputDirectoryName = when (inputSelection) {
            CacheRootSelection.Default -> DefaultCacheDirectory.DISPLAY_PATH
            is CacheRootSelection.Tree -> DocumentFile.fromTreeUri(this, inputSelection.uri)?.name
                ?: getString(R.string.directory_custom_value)
        }
        inputDirectoryValue.text = inputDirectoryName
        inputDirectoryRow.contentDescription = getString(
            R.string.input_directory_setting_accessibility_description,
            inputDirectoryName
        )

        val outputDirectoryRow = findViewById<View>(R.id.rowOutputDirectory)
        val outputDirectoryValue = findViewById<TextView>(R.id.tvOutputDirectoryValue)
        val outputDirectoryName = outputDirectoryStore.displayName()
            ?: if (outputDirectoryStore.currentTreeUri() == null) {
                OutputDirectoryStore.DEFAULT_RELATIVE_PATH
            } else {
                getString(R.string.directory_custom_value)
            }
        outputDirectoryValue.text = outputDirectoryName
        outputDirectoryRow.contentDescription = getString(
            R.string.output_directory_setting_accessibility_description,
            outputDirectoryName
        )
    }

    private fun showDirectoryPermissionError() {
        Toast.makeText(this, R.string.directory_permission_failed, Toast.LENGTH_LONG).show()
    }

    private fun updateMp3BitrateValue(bitrateRow: View, bitrateValue: TextView) {
        val bitrate = AppSettings.getMp3BitrateKbps(this)
        bitrateValue.text = getString(R.string.mp3_bitrate_value_format, bitrate)
        bitrateRow.contentDescription = getString(
            R.string.mp3_bitrate_setting_accessibility_description,
            bitrate
        )
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
