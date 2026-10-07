package com.example.bili2media

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bili2media.cache.model.BiliCacheEntry
import com.example.bili2media.cache.scanner.DocumentTreeBiliCacheScanner
import com.example.bili2media.cache.scanner.FileBiliCacheScanner
import com.example.bili2media.export.m4a.output.MediaStoreM4aOutputStore
import com.example.bili2media.export.output.CacheEntryExportTitleResolver
import com.example.bili2media.export.output.MediaStoreMp4OutputStore
import com.example.bili2media.storage.CacheRootSelection
import com.example.bili2media.storage.CacheRootStore
import com.example.bili2media.storage.DefaultCacheDirectory
import com.example.bili2media.storage.InputDirectoryObserver
import com.example.bili2media.ui.BiliCacheAdapter
import com.example.bili2media.ui.common.showRounded
import com.example.bili2media.ui.export.BiliCacheListItem
import com.example.bili2media.ui.export.Mp4ExportUiState
import com.example.bili2media.ui.export.Mp4ExportViewModel
import com.example.bili2media.ui.export.m4a.M4aExportUiState
import com.example.bili2media.ui.export.m4a.M4aExportViewModel
import com.example.bili2media.ui.image.CoilCoverImageLoader
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : AppCompatActivity() {
    private lateinit var txtInputDirectory: TextView
    private lateinit var txtOutputDirectory: TextView
    private lateinit var btnHelp: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var progressScan: ProgressBar
    private lateinit var txtContentMessage: TextView
    private lateinit var recyclerCaches: RecyclerView

    private val cacheAdapter by lazy {
        BiliCacheAdapter(
            coverImageLoader = CoilCoverImageLoader(),
            onExport = ::enqueueExport,
            onM4aExport = ::enqueueM4aExport
        )
    }
    private val scanExecutor = Executors.newSingleThreadExecutor()
    private val scanGeneration = AtomicInteger(0)
    private val rootStore by lazy { CacheRootStore(this) }
    private val mp4OutputStore by lazy { MediaStoreMp4OutputStore(this) }
    private val m4aOutputStore by lazy { MediaStoreM4aOutputStore(this) }
    private val exportTitleResolver by lazy { CacheEntryExportTitleResolver() }
    private val permissionPreferences by lazy {
        getSharedPreferences(PERMISSION_PREFERENCES_NAME, MODE_PRIVATE)
    }

    private var initialized = false
    private var lastAllFilesAccess = false
    private var needsScanOnResume = false
    private var inputDirectoryObserver: InputDirectoryObserver? = null
    private var scannedEntries: List<BiliCacheEntry> = emptyList()
    private var latestExportStates: Map<String, Mp4ExportUiState> = emptyMap()
    private var latestM4aExportStates: Map<String, M4aExportUiState> = emptyMap()
    private var exportProgressDialog: AlertDialog? = null
    private var exportProgressMessage: TextView? = null
    private var exportProgressBar: ProgressBar? = null
    private var exportProgressCancelButton: Button? = null
    private var exportProgressTask: ExportTaskKey? = null
    private var exportCancellationRequested = false
    private lateinit var exportViewModel: Mp4ExportViewModel
    private lateinit var m4aExportViewModel: M4aExportViewModel

    private val allFilesAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        initialized = true
        lastAllFilesAccess = Environment.isExternalStorageManager()
        needsScanOnResume = false
        refreshHeaderAndScan()
    }

    private val directoryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            handleSelectedTree(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        fitContentBelowSystemBars(
            findViewById(R.id.main),
            findViewById(R.id.statusBarBackground)
        )
        txtInputDirectory = findViewById(R.id.txtInputDirectory)
        txtOutputDirectory = findViewById(R.id.txtOutputDirectory)
        btnHelp = findViewById(R.id.btnHelp)
        btnSettings = findViewById(R.id.btnSettings)
        txtOutputDirectory.text = getString(
            R.string.directory_path_format,
            MediaStoreMp4OutputStore.OUTPUT_RELATIVE_PATH
        )
        progressScan = findViewById(R.id.progressScan)
        txtContentMessage = findViewById(R.id.txtContentMessage)
        recyclerCaches = findViewById(R.id.recyclerCaches)

        recyclerCaches.layoutManager = LinearLayoutManager(this)
        recyclerCaches.adapter = cacheAdapter

        exportViewModel = ViewModelProvider(this)[Mp4ExportViewModel::class.java]
        exportViewModel.states.observe(this) { states ->
            latestExportStates = states.orEmpty()
            updateExportProgressDialog()
        }
        m4aExportViewModel = ViewModelProvider(this)[M4aExportViewModel::class.java]
        m4aExportViewModel.states.observe(this) { states ->
            latestM4aExportStates = states.orEmpty()
            updateExportProgressDialog()
        }

        btnHelp.setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun fitContentBelowSystemBars(rootView: View, statusBarBackground: View) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val statusBarColor = ContextCompat.getColor(this, R.color.bili2media_background)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = ContextCompat.getColor(this, R.color.white)
        WindowInsetsControllerCompat(window, rootView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
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

    override fun onResume() {
        super.onResume()
        if (showStoragePermissionNoticeIfNeeded()) {
            return
        }

        val hasAllFilesAccess = Environment.isExternalStorageManager()
        if (!initialized || hasAllFilesAccess != lastAllFilesAccess || needsScanOnResume) {
            initialized = true
            lastAllFilesAccess = hasAllFilesAccess
            needsScanOnResume = false
            refreshHeaderAndScan()
        }
    }

    override fun onPause() {
        needsScanOnResume = true
        stopInputDirectoryObserver()
        super.onPause()
    }

    override fun onDestroy() {
        stopInputDirectoryObserver()
        scanGeneration.incrementAndGet()
        scanExecutor.shutdownNow()
        dismissExportProgressDialog()
        exportProgressTask = null
        exportCancellationRequested = false
        super.onDestroy()
    }

    private fun handleSelectedTree(uri: Uri) {
        val persisted = runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }.isSuccess
        if (!persisted) {
            showMessage(getString(R.string.directory_permission_failed), isError = true)
            return
        }

        rootStore.saveTree(uri)
        refreshHeaderAndScan()
    }

    private fun openAllFilesAccessSettings() {
        val appIntent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            "package:$packageName".toUri()
        )
        try {
            allFilesAccessLauncher.launch(appIntent)
        } catch (_: ActivityNotFoundException) {
            allFilesAccessLauncher.launch(
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            )
        }
    }

    private fun showStoragePermissionNoticeIfNeeded(): Boolean {
        if (permissionPreferences.getBoolean(KEY_STORAGE_PERMISSION_PROMPTED, false)) {
            return false
        }

        if (Environment.isExternalStorageManager()) {
            permissionPreferences.edit()
                .putBoolean(KEY_STORAGE_PERMISSION_PROMPTED, true)
                .apply()
            return false
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.storage_permission_title)
            .setMessage(R.string.storage_permission_message)
            .setPositiveButton(R.string.storage_permission_open_settings) { _, _ ->
                permissionPreferences.edit()
                    .putBoolean(KEY_STORAGE_PERMISSION_PROMPTED, true)
                    .apply()
                openAllFilesAccessSettings()
            }
            .setCancelable(false)
            .showRounded()

        return true
    }

    private fun refreshHeaderAndScan() {
        val selection = rootStore.current()
        renderHeader(selection)
        when (selection) {
            CacheRootSelection.Default -> scanDefaultDirectory()
            is CacheRootSelection.Tree -> {
                stopInputDirectoryObserver()
                scanDocumentTree(selection.uri)
            }
        }
    }

    private fun renderHeader(selection: CacheRootSelection) {
        when (selection) {
            CacheRootSelection.Default -> {
                txtInputDirectory.text = getString(
                    R.string.directory_path_format,
                    DefaultCacheDirectory.DISPLAY_PATH
                )
            }

            is CacheRootSelection.Tree -> {
                val rootName = DocumentFile.fromTreeUri(this, selection.uri)?.name
                    ?: selection.uri.toString()
                txtInputDirectory.text = getString(
                    R.string.directory_path_format,
                    rootName
                )
            }
        }
    }

    private fun scanDefaultDirectory() {
        if (!Environment.isExternalStorageManager()) {
            stopInputDirectoryObserver()
            scanGeneration.incrementAndGet()
            showMessage(getString(R.string.permission_required_message), isError = false)
            return
        }

        val root = DefaultCacheDirectory.file()
        ensureInputDirectoryObserver(root)
        val directoryReady = when {
            root.isDirectory -> true
            root.exists() -> false
            else -> runCatching { root.mkdirs() }.getOrDefault(false)
        }
        if (!directoryReady) {
            showMessage(getString(R.string.default_directory_create_failed), isError = true)
            return
        }

        runScan {
            FileBiliCacheScanner().scan(root)
        }
    }

    private fun ensureInputDirectoryObserver(directory: File) {
        if (inputDirectoryObserver != null) return
        inputDirectoryObserver = InputDirectoryObserver(directory) {
            if (!isFinishing && !isDestroyed) refreshHeaderAndScan()
        }.also { it.start() }
    }

    private fun stopInputDirectoryObserver() {
        inputDirectoryObserver?.stop()
        inputDirectoryObserver = null
    }

    private fun scanDocumentTree(uri: Uri) {
        val root = DocumentFile.fromTreeUri(this, uri)
        if (root == null || !root.isDirectory || !root.canRead()) {
            rootStore.clearTree()
            scanGeneration.incrementAndGet()
            renderHeader(CacheRootSelection.Default)
            showMessage(getString(R.string.directory_permission_expired), isError = true)
            return
        }

        runScan {
            DocumentTreeBiliCacheScanner(contentResolver).scan(root)
        }
    }

    private fun runScan(scanner: () -> List<BiliCacheEntry>) {
        val generation = scanGeneration.incrementAndGet()
        showLoading()
        scanExecutor.execute {
            val result = runCatching(scanner)
            runOnUiThread {
                if (generation != scanGeneration.get() || isFinishing || isDestroyed) {
                    return@runOnUiThread
                }
                result.onSuccess(::showEntries)
                    .onFailure {
                        showMessage(getString(R.string.scan_failed), isError = true)
                    }
            }
        }
    }

    private fun showLoading() {
        scannedEntries = emptyList()
        submitCombinedList()
        progressScan.visibility = View.VISIBLE
        recyclerCaches.visibility = View.GONE
        txtContentMessage.visibility = View.GONE
    }

    private fun showEntries(entries: List<BiliCacheEntry>) {
        progressScan.visibility = View.GONE
        if (entries.isEmpty()) {
            scannedEntries = emptyList()
            submitCombinedList()
            recyclerCaches.visibility = View.GONE
            txtContentMessage.text = getString(R.string.no_cache_found)
            txtContentMessage.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            txtContentMessage.setTypeface(null, Typeface.ITALIC)
            txtContentMessage.setTextColor(getColor(R.color.bili2media_text_secondary))
            txtContentMessage.visibility = View.VISIBLE
            return
        }

        scannedEntries = entries
        submitCombinedList()
        txtContentMessage.visibility = View.GONE
        recyclerCaches.visibility = View.VISIBLE
    }

    private fun showMessage(message: String, isError: Boolean) {
        progressScan.visibility = View.GONE
        recyclerCaches.visibility = View.GONE
        scannedEntries = emptyList()
        submitCombinedList()
        txtContentMessage.text = message
        txtContentMessage.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            resources.getDimension(R.dimen.text_body)
        )
        txtContentMessage.setTypeface(null, Typeface.NORMAL)
        txtContentMessage.setTextColor(
            getColor(
                if (isError) R.color.bili2media_danger else R.color.bili2media_text_secondary
            )
        )
        txtContentMessage.visibility = View.VISIBLE
        renderHeader(rootStore.current())
    }

    private fun submitCombinedList() {
        cacheAdapter.submitList(
            scannedEntries.map { entry ->
                BiliCacheListItem(entry)
            }
        )
    }

    private fun enqueueExport(entry: BiliCacheEntry) {
        val title = exportTitleResolver.resolve(entry.title, entry.subtitle)
        val duplicateName = mp4OutputStore.suggestDuplicateName(title)
        if (duplicateName == null) {
            startMp4Export(entry)
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.export_duplicate_title)
            .setMessage(getString(R.string.export_duplicate_message, duplicateName))
            .setPositiveButton(R.string.export_duplicate_continue) { _, _ ->
                startMp4Export(entry)
            }
            .setNegativeButton(R.string.cancel_export, null)
            .showRounded()
    }

    private fun startMp4Export(entry: BiliCacheEntry) {
        exportViewModel.enqueue(entry)
    }

    private fun enqueueM4aExport(entry: BiliCacheEntry) {
        val title = exportTitleResolver.resolve(entry.title, entry.subtitle)
        val duplicateName = m4aOutputStore.suggestDuplicateName(title)
        if (duplicateName == null) {
            startM4aExport(entry)
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.export_duplicate_title)
            .setMessage(getString(R.string.export_duplicate_message, duplicateName))
            .setPositiveButton(R.string.export_duplicate_continue) { _, _ ->
                startM4aExport(entry)
            }
            .setNegativeButton(R.string.cancel_export, null)
            .showRounded()
    }

    private fun startM4aExport(entry: BiliCacheEntry) {
        m4aExportViewModel.enqueue(entry)
    }

    private fun updateExportProgressDialog() {
        while (true) {
            val task = exportProgressTask ?: firstActiveExportTask()
            if (task == null) {
                dismissExportProgressDialog()
                return
            }
            exportProgressTask = task

            val progress = progressInfo(task)
            if (progress == null) {
                exportProgressTask = null
                exportCancellationRequested = false
                dismissExportProgressDialog()
                continue
            }

            val completion = progress.completion
            if (completion != null) {
                exportProgressTask = null
                exportCancellationRequested = false
                dismissExportProgressDialog()
                when (completion) {
                    ExportCompletion.SUCCEEDED -> Toast.makeText(
                        this,
                        R.string.export_status_succeeded,
                        Toast.LENGTH_SHORT
                    ).show()

                    ExportCompletion.FAILED -> Toast.makeText(
                        this,
                        R.string.export_status_failed,
                        Toast.LENGTH_LONG
                    ).show()

                    ExportCompletion.CANCELLED -> Unit
                }
                continue
            }

            ensureExportProgressDialog(task)
            val percent = progress.progress
            if (exportCancellationRequested) {
                exportProgressMessage?.setText(R.string.export_status_cancelling)
                exportProgressBar?.isIndeterminate = true
            } else if (percent == null) {
                exportProgressMessage?.setText(progress.messageRes)
                exportProgressBar?.isIndeterminate = true
            } else {
                exportProgressMessage?.text = getString(
                    R.string.export_status_progress,
                    percent
                )
                exportProgressBar?.apply {
                    isIndeterminate = false
                    this.progress = percent.coerceIn(0, 100)
                }
            }
            return
        }
    }

    private fun firstActiveExportTask(): ExportTaskKey? {
        latestExportStates.entries.firstOrNull { (_, state) ->
            state.toProgressInfo()?.completion == null &&
                state.toProgressInfo() != null
        }?.let { return ExportTaskKey(it.key, ExportFormat.MP4) }

        latestM4aExportStates.entries.firstOrNull { (_, state) ->
            state.toProgressInfo()?.completion == null &&
                state.toProgressInfo() != null
        }?.let { return ExportTaskKey(it.key, ExportFormat.M4A) }
        return null
    }

    private fun progressInfo(task: ExportTaskKey): ExportProgressInfo? {
        return when (task.format) {
            ExportFormat.MP4 -> latestExportStates[task.entryId]?.toProgressInfo()
            ExportFormat.M4A -> latestM4aExportStates[task.entryId]?.toProgressInfo()
        }
    }

    private fun Mp4ExportUiState.toProgressInfo(): ExportProgressInfo? {
        return when (this) {
            Mp4ExportUiState.Idle -> null
            Mp4ExportUiState.Queued -> ExportProgressInfo(R.string.export_status_queued)
            Mp4ExportUiState.Analyzing -> ExportProgressInfo(R.string.export_status_analyzing)
            is Mp4ExportUiState.Exporting -> ExportProgressInfo(
                messageRes = R.string.export_status_progress,
                progress = progress
            )

            is Mp4ExportUiState.Succeeded -> ExportProgressInfo(
                messageRes = R.string.export_status_succeeded,
                completion = ExportCompletion.SUCCEEDED
            )

            is Mp4ExportUiState.Failed -> ExportProgressInfo(
                messageRes = R.string.export_status_failed,
                completion = ExportCompletion.FAILED
            )

            Mp4ExportUiState.Cancelled -> ExportProgressInfo(
                messageRes = R.string.export_status_cancelled,
                completion = ExportCompletion.CANCELLED
            )
        }
    }

    private fun M4aExportUiState.toProgressInfo(): ExportProgressInfo? {
        return when (this) {
            M4aExportUiState.Idle -> null
            M4aExportUiState.Queued -> ExportProgressInfo(R.string.export_status_queued)
            M4aExportUiState.Analyzing -> ExportProgressInfo(R.string.export_status_analyzing)
            is M4aExportUiState.Exporting -> ExportProgressInfo(
                messageRes = R.string.export_status_progress,
                progress = progress
            )

            is M4aExportUiState.Succeeded -> ExportProgressInfo(
                messageRes = R.string.export_status_succeeded,
                completion = ExportCompletion.SUCCEEDED
            )

            is M4aExportUiState.Failed -> ExportProgressInfo(
                messageRes = R.string.export_status_failed,
                completion = ExportCompletion.FAILED
            )

            M4aExportUiState.Cancelled -> ExportProgressInfo(
                messageRes = R.string.export_status_cancelled,
                completion = ExportCompletion.CANCELLED
            )
        }
    }

    private fun ensureExportProgressDialog(task: ExportTaskKey) {
        if (exportProgressDialog != null) return

        val message = TextView(this).apply {
            setTextColor(getColor(R.color.bili2media_text_secondary))
            setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.text_body)
            )
        }
        val progressBar = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            isIndeterminate = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(16))
            addView(
                message,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                progressBar,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(8)
                ).apply { topMargin = dp(12) }
            )
        }
        val titleRes = when (task.format) {
            ExportFormat.MP4 -> R.string.export_dialog_mp4_title
            ExportFormat.M4A -> R.string.export_dialog_m4a_title
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setView(content)
            .setNegativeButton(R.string.cancel_export, null)
            .showRounded()
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.setOnClickListener {
            cancelCurrentExport()
        }

        exportProgressDialog = dialog
        exportProgressMessage = message
        exportProgressBar = progressBar
        exportProgressCancelButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
    }

    private fun cancelCurrentExport() {
        val task = exportProgressTask ?: return
        if (exportCancellationRequested) return

        exportCancellationRequested = true
        exportProgressMessage?.setText(R.string.export_status_cancelling)
        exportProgressBar?.isIndeterminate = true
        exportProgressCancelButton?.isEnabled = false
        when (task.format) {
            ExportFormat.MP4 -> exportViewModel.cancel(task.entryId)
            ExportFormat.M4A -> m4aExportViewModel.cancel(task.entryId)
        }
    }

    private fun dismissExportProgressDialog() {
        exportProgressDialog?.dismiss()
        exportProgressDialog = null
        exportProgressMessage = null
        exportProgressBar = null
        exportProgressCancelButton = null
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private enum class ExportFormat {
        MP4,
        M4A
    }

    private enum class ExportCompletion {
        SUCCEEDED,
        FAILED,
        CANCELLED
    }

    private data class ExportTaskKey(
        val entryId: String,
        val format: ExportFormat
    )

    private data class ExportProgressInfo(
        val messageRes: Int,
        val progress: Int? = null,
        val completion: ExportCompletion? = null
    )

    private companion object {
        const val PERMISSION_PREFERENCES_NAME = "storage_permission"
        const val KEY_STORAGE_PERMISSION_PROMPTED = "storage_permission_prompted"
    }
}
