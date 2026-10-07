package com.example.bili2media.storage

import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import java.io.File

class InputDirectoryObserver(
    private val inputDirectory: File,
    private val onChanged: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val observers = mutableMapOf<String, FileObserver>()

    @Volatile
    private var started = false
    private var pendingScan: Runnable? = null
    private var pendingObserverSync: Runnable? = null

    fun start() {
        if (started) return
        started = true
        syncObservers()
    }

    fun stop() {
        started = false
        pendingScan?.let(handler::removeCallbacks)
        pendingScan = null
        pendingObserverSync?.let(handler::removeCallbacks)
        pendingObserverSync = null
        observers.values.forEach(FileObserver::stopWatching)
        observers.clear()
    }

    private fun onFileEvent(event: Int) {
        handler.post {
            if (!started) return@post

            if (event and DIRECTORY_TOPOLOGY_EVENTS != 0) {
                scheduleObserverSync()
            }
            scheduleScan()
        }
    }

    private fun scheduleObserverSync() {
        pendingObserverSync?.let(handler::removeCallbacks)
        val task = Runnable {
            pendingObserverSync = null
            if (started) syncObservers()
        }
        pendingObserverSync = task
        handler.postDelayed(task, OBSERVER_SYNC_DELAY_MS)
    }

    private fun scheduleScan() {
        pendingScan?.let(handler::removeCallbacks)
        val task = Runnable {
            pendingScan = null
            if (started) onChanged()
        }
        pendingScan = task
        handler.postDelayed(task, SCAN_DEBOUNCE_MS)
    }

    private fun syncObservers() {
        if (!started) return

        val directories = linkedMapOf<String, File>()
        findNearestExistingDirectory(inputDirectory.parentFile)?.let { parent ->
            directories[parent.canonicalPathOrAbsolute()] = parent
        }
        if (inputDirectory.isDirectory) {
            collectDirectoryTree(inputDirectory, directories, mutableSetOf())
        }

        (observers.keys - directories.keys).forEach { stalePath ->
            observers.remove(stalePath)?.stopWatching()
        }

        directories.forEach { (path, directory) ->
            if (path !in observers) {
                val observer = object : FileObserver(directory, WATCH_EVENTS) {
                    override fun onEvent(event: Int, path: String?) {
                        onFileEvent(event)
                    }
                }
                if (runCatching { observer.startWatching() }.isSuccess) {
                    observers[path] = observer
                }
            }
        }
    }

    private fun findNearestExistingDirectory(start: File?): File? {
        var current = start
        while (current != null && !current.isDirectory) {
            current = current.parentFile
        }
        return current
    }

    private fun collectDirectoryTree(
        directory: File,
        result: MutableMap<String, File>,
        visitedPaths: MutableSet<String>
    ) {
        if (!directory.isDirectory) return
        val canonicalDirectory = runCatching { directory.canonicalFile }
            .getOrDefault(directory.absoluteFile)
        val path = canonicalDirectory.path
        if (!visitedPaths.add(path)) return

        result[path] = canonicalDirectory
        val childDirectories = runCatching {
            canonicalDirectory.listFiles()?.filter { it.isDirectory }.orEmpty()
        }.getOrDefault(emptyList())
        childDirectories.forEach { child ->
            collectDirectoryTree(child, result, visitedPaths)
        }
    }

    private fun File.canonicalPathOrAbsolute(): String =
        runCatching { canonicalPath }.getOrDefault(absolutePath)

    private companion object {
        const val WATCH_EVENTS = FileObserver.CREATE or
            FileObserver.CLOSE_WRITE or
            FileObserver.MODIFY or
            FileObserver.ATTRIB or
            FileObserver.DELETE or
            FileObserver.MOVED_FROM or
            FileObserver.MOVED_TO or
            FileObserver.DELETE_SELF or
            FileObserver.MOVE_SELF
        const val DIRECTORY_TOPOLOGY_EVENTS = FileObserver.CREATE or
            FileObserver.DELETE or
            FileObserver.MOVED_FROM or
            FileObserver.MOVED_TO or
            FileObserver.DELETE_SELF or
            FileObserver.MOVE_SELF
        const val OBSERVER_SYNC_DELAY_MS = 200L
        const val SCAN_DEBOUNCE_MS = 800L
    }
}
