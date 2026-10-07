package com.example.bili2media.cache.media

import com.example.bili2media.cache.model.CacheEntryLocation
import com.example.bili2media.cache.model.CacheMediaFile
import com.example.bili2media.cache.model.MediaInputRef
import com.example.bili2media.cache.policy.BiliCacheEntryFileSelector
import java.io.File
import java.util.ArrayDeque
import java.util.Locale

class FileCacheMediaLocator(
    private val entryFileSelector: BiliCacheEntryFileSelector = BiliCacheEntryFileSelector()
) : CacheMediaLocator {
    override fun locate(location: CacheEntryLocation): List<CacheMediaFile> {
        val fileLocation = location as? CacheEntryLocation.FileDirectory ?: return emptyList()
        val root = runCatching { File(fileLocation.path).canonicalFile }.getOrNull()
            ?: return emptyList()
        if (!root.isDirectory) {
            return emptyList()
        }

        val rootPath = root.toPath()
        val pending = ArrayDeque<Node>().apply { add(Node(root, "")) }
        val visited = mutableSetOf<String>()
        val result = mutableListOf<CacheMediaFile>()

        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            val directory = runCatching { node.file.canonicalFile }.getOrNull() ?: continue
            if (!directory.toPath().startsWith(rootPath) ||
                !visited.add(directory.path) ||
                !directory.isDirectory
            ) {
                continue
            }

            val children = safeListFiles(directory)
            if (node.relativePath.isNotEmpty() && selectEntryFile(children) != null) {
                continue
            }

            children.forEach { child ->
                val childRelativePath = joinPath(node.relativePath, child.name)
                when {
                    child.isDirectory -> pending.add(Node(child, childRelativePath))
                    child.isFile && isExportInputFileName(child.name) -> {
                        val canonicalChild = runCatching { child.canonicalFile }.getOrNull()
                            ?: return@forEach
                        if (!canonicalChild.toPath().startsWith(rootPath)) {
                            return@forEach
                        }
                        result += CacheMediaFile(
                            name = child.name,
                            relativePath = childRelativePath,
                            size = runCatching { child.length() }.getOrDefault(0L)
                                .coerceAtLeast(0L),
                            input = MediaInputRef.FilePath(canonicalChild.path)
                        )
                    }
                }
            }
        }

        return result.sortedWith(
            compareBy<CacheMediaFile> { it.relativePath.lowercase(Locale.ROOT) }
                .thenBy { it.relativePath }
        )
    }

    private fun selectEntryFile(files: List<File>): File? {
        return entryFileSelector.select(files.filter { it.isFile }) { it.name }
    }

    private fun safeListFiles(directory: File): List<File> {
        return runCatching { directory.listFiles()?.toList().orEmpty() }
            .getOrDefault(emptyList())
    }

    private fun joinPath(parent: String, child: String): String {
        return listOf(parent, child).filter { it.isNotEmpty() }.joinToString("/")
    }

    private data class Node(
        val file: File,
        val relativePath: String
    )
}
