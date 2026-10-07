package com.example.bili2media

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bili2media.cache.model.BiliCacheEntry
import com.example.bili2media.cache.scanner.DocumentTreeBiliCacheScanner
import com.example.bili2media.cache.scanner.FileBiliCacheScanner
import com.example.bili2media.storage.CacheRootSelection
import com.example.bili2media.storage.CacheRootStore
import com.example.bili2media.storage.DefaultCacheDirectory
import com.example.bili2media.ui.BiliCacheAdapter
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
    private lateinit var txtCurrentDirectory: TextView
    private lateinit var txtAccessStatus: TextView
    private lateinit var btnGrantAccess: Button
    private lateinit var btnChooseDirectory: Button
    private lateinit var btnRefresh: Button
    private lateinit var progressScan: ProgressBar
    private lateinit var txtContentMessage: TextView
    private lateinit var recyclerCaches: RecyclerView

    private val cacheAdapter by lazy {
        BiliCacheAdapter(
            coverImageLoader = CoilCoverImageLoader(),
            onExport = ::enqueueExport,
            onCancel = ::cancelExport,
            onOpenOutput = ::openExportedMp4,
            onM4aExport = ::enqueueM4aExport,
            onM4aCancel = ::cancelM4aExport,
            onOpenM4aOutput = ::openExportedM4a
        )
    }
    private val scanExecutor = Executors.newSingleThreadExecutor()
    private val scanGeneration = AtomicInteger(0)
    private val rootStore by lazy { CacheRootStore(this) }

    private var initialized = false
    private var lastAllFilesAccess = false
    private var notificationPermissionRequested = false
    private var scannedEntries: List<BiliCacheEntry> = emptyList()
    private var latestExportStates: Map<String, Mp4ExportUiState> = emptyMap()
    private var latestM4aExportStates: Map<String, M4aExportUiState> = emptyMap()
    private lateinit var exportViewModel: Mp4ExportViewModel
    private lateinit var m4aExportViewModel: M4aExportViewModel

    private val allFilesAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshHeaderAndScan()
    }

    private val directoryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            handleSelectedTree(uri)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Export continues regardless of notification permission result.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        notificationPermissionRequested = savedInstanceState?.getBoolean(
            STATE_NOTIFICATION_PERMISSION_REQUESTED,
            false
        ) ?: false

        txtCurrentDirectory = findViewById(R.id.txtCurrentDirectory)
        txtAccessStatus = findViewById(R.id.txtAccessStatus)
        btnGrantAccess = findViewById(R.id.btnGrantAccess)
        btnChooseDirectory = findViewById(R.id.btnChooseDirectory)
        btnRefresh = findViewById(R.id.btnRefresh)
        progressScan = findViewById(R.id.progressScan)
        txtContentMessage = findViewById(R.id.txtContentMessage)
        recyclerCaches = findViewById(R.id.recyclerCaches)

        recyclerCaches.layoutManager = LinearLayoutManager(this)
        recyclerCaches.adapter = cacheAdapter

        exportViewModel = ViewModelProvider(this)[Mp4ExportViewModel::class.java]
        exportViewModel.states.observe(this) { states ->
            latestExportStates = states.orEmpty()
            submitCombinedList()
        }
        m4aExportViewModel = ViewModelProvider(this)[M4aExportViewModel::class.java]
        m4aExportViewModel.states.observe(this) { states ->
            latestM4aExportStates = states.orEmpty()
            submitCombinedList()
        }

        btnGrantAccess.setOnClickListener {
            if (Environment.isExternalStorageManager()) {
                rootStore.useDefault()
                refreshHeaderAndScan()
            } else {
                openAllFilesAccessSettings()
            }
        }
        btnChooseDirectory.setOnClickListener {
            directoryPickerLauncher.launch(null)
        }
        btnRefresh.setOnClickListener {
            refreshHeaderAndScan()
        }
    }

    override fun onResume() {
        super.onResume()
        val hasAllFilesAccess = Environment.isExternalStorageManager()
        if (!initialized || hasAllFilesAccess != lastAllFilesAccess) {
            initialized = true
            lastAllFilesAccess = hasAllFilesAccess
            refreshHeaderAndScan()
        }
    }

    override fun onDestroy() {
        scanGeneration.incrementAndGet()
        scanExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(
            STATE_NOTIFICATION_PERMISSION_REQUESTED,
            notificationPermissionRequested
        )
        super.onSaveInstanceState(outState)
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

    private fun refreshHeaderAndScan() {
        val selection = rootStore.current()
        renderHeader(selection)
        when (selection) {
            CacheRootSelection.Default -> scanDefaultDirectory()
            is CacheRootSelection.Tree -> scanDocumentTree(selection.uri)
        }
    }

    private fun renderHeader(selection: CacheRootSelection) {
        val hasAllFilesAccess = Environment.isExternalStorageManager()
        btnGrantAccess.text = getString(
            if (hasAllFilesAccess) R.string.use_default_directory else R.string.grant_access
        )

        when (selection) {
            CacheRootSelection.Default -> {
                txtCurrentDirectory.text = getString(
                    R.string.current_directory_format,
                    DefaultCacheDirectory.DISPLAY_PATH
                )
                txtAccessStatus.text = getString(
                    if (hasAllFilesAccess) {
                        R.string.default_directory_access_granted
                    } else {
                        R.string.default_directory_access_required
                    }
                )
                btnRefresh.isEnabled = hasAllFilesAccess
            }

            is CacheRootSelection.Tree -> {
                val rootName = DocumentFile.fromTreeUri(this, selection.uri)?.name
                    ?: selection.uri.toString()
                txtCurrentDirectory.text = getString(R.string.current_directory_format, rootName)
                txtAccessStatus.text = getString(R.string.tree_directory_access_granted)
                btnRefresh.isEnabled = true
            }
        }
    }

    private fun scanDefaultDirectory() {
        if (!Environment.isExternalStorageManager()) {
            scanGeneration.incrementAndGet()
            showMessage(getString(R.string.permission_required_message), isError = false)
            return
        }

        val root = DefaultCacheDirectory.file()
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
        btnRefresh.isEnabled = false
    }

    private fun showEntries(entries: List<BiliCacheEntry>) {
        progressScan.visibility = View.GONE
        btnRefresh.isEnabled = true
        if (entries.isEmpty()) {
            scannedEntries = emptyList()
            submitCombinedList()
            recyclerCaches.visibility = View.GONE
            txtContentMessage.text = getString(R.string.no_cache_found)
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
                BiliCacheListItem(
                    entry = entry,
                    exportState = latestExportStates[entry.id] ?: Mp4ExportUiState.Idle,
                    m4aExportState = latestM4aExportStates[entry.id]
                        ?: M4aExportUiState.Idle
                )
            }
        )
    }

    private fun enqueueExport(entry: BiliCacheEntry) {
        exportViewModel.enqueue(entry)
        requestNotificationPermissionIfNeeded()
    }

    private fun cancelExport(entryId: String) {
        exportViewModel.cancel(entryId)
    }

    private fun enqueueM4aExport(entry: BiliCacheEntry) {
        m4aExportViewModel.enqueue(entry)
        requestNotificationPermissionIfNeeded()
    }

    private fun cancelM4aExport(entryId: String) {
        m4aExportViewModel.cancel(entryId)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (notificationPermissionRequested ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationPermissionRequested = true
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun openExportedMp4(uriValue: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(uriValue), "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.open_mp4_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openExportedM4a(uriValue: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(uriValue), "audio/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.open_m4a_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val STATE_NOTIFICATION_PERMISSION_REQUESTED =
            "notification_permission_requested"
    }
}
