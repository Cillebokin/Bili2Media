package com.example.bili2media

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bili2media.cache.model.BiliCacheEntry
import com.example.bili2media.cache.scanner.DocumentTreeBiliCacheScanner
import com.example.bili2media.cache.scanner.FileBiliCacheScanner
import com.example.bili2media.storage.CacheRootSelection
import com.example.bili2media.storage.CacheRootStore
import com.example.bili2media.storage.DefaultCacheDirectory
import com.example.bili2media.ui.BiliCacheAdapter
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

    private val cacheAdapter = BiliCacheAdapter(CoilCoverImageLoader())
    private val scanExecutor = Executors.newSingleThreadExecutor()
    private val scanGeneration = AtomicInteger(0)
    private val rootStore by lazy { CacheRootStore(this) }

    private var initialized = false
    private var lastAllFilesAccess = false

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
        cacheAdapter.submitList(emptyList())
        progressScan.visibility = View.VISIBLE
        recyclerCaches.visibility = View.GONE
        txtContentMessage.visibility = View.GONE
        btnRefresh.isEnabled = false
    }

    private fun showEntries(entries: List<BiliCacheEntry>) {
        progressScan.visibility = View.GONE
        btnRefresh.isEnabled = true
        if (entries.isEmpty()) {
            cacheAdapter.submitList(emptyList())
            recyclerCaches.visibility = View.GONE
            txtContentMessage.text = getString(R.string.no_cache_found)
            txtContentMessage.setTextColor(getColor(R.color.bili2media_text_secondary))
            txtContentMessage.visibility = View.VISIBLE
            return
        }

        cacheAdapter.submitList(entries)
        txtContentMessage.visibility = View.GONE
        recyclerCaches.visibility = View.VISIBLE
    }

    private fun showMessage(message: String, isError: Boolean) {
        progressScan.visibility = View.GONE
        recyclerCaches.visibility = View.GONE
        cacheAdapter.submitList(emptyList())
        txtContentMessage.text = message
        txtContentMessage.setTextColor(
            getColor(
                if (isError) R.color.bili2media_danger else R.color.bili2media_text_secondary
            )
        )
        txtContentMessage.visibility = View.VISIBLE
        renderHeader(rootStore.current())
    }
}
