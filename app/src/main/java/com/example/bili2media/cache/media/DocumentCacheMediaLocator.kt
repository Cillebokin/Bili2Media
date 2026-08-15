package com.example.bili2media.cache.media

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.example.bili2media.cache.model.CacheEntryLocation
import com.example.bili2media.cache.model.CacheMediaFile
import com.example.bili2media.cache.model.MediaInputRef
import com.example.bili2media.cache.policy.BiliCacheEntryFileSelector
import java.util.ArrayDeque
import java.util.Locale

class DocumentCacheMediaLocator(
    private val documentResolver: (String) -> DocumentFile?,
    private val entryFileSelector: BiliCacheEntryFileSelector = BiliCacheEntryFileSelector()
) : CacheMediaLocator {
    constructor(context: Context) : this(
        documentResolver = { uri -> DocumentFile.fromSingleUri(context, uri.toUri()) }
    )

    override fun locate(location: CacheEntryLocation): List<CacheMediaFile> {
        val documentLocation = location as? CacheEntryLocation.DocumentDirectory
            ?: return emptyList()
        val root = runCatching { documentResolver(documentLocation.uri) }.getOrNull()
            ?: return emptyList()
        if (!root.isDirectory || !root.canRead()) {
            return emptyList()
        }

        val pending = ArrayDeque<Node>().apply { add(Node(root, "")) }
        val visited = mutableSetOf<String>()
        val result = mutableListOf<CacheMediaFile>()

        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            val directory = node.file
            if (!visited.add(directory.uri.toString()) || !directory.isDirectory) {
                continue
            }

            val children = safeListFiles(directory)
            if (node.relativePath.isNotEmpty() && selectEntryFile(children) != null) {
                continue
            }

            children.forEach { child ->
                val childName = child.name.orEmpty().ifBlank {
                    child.uri.lastPathSegment.orEmpty()
                }
                val childRelativePath = joinPath(node.relativePath, childName)
                when {
                    child.isDirectory -> pending.add(Node(child, childRelativePath))
                    child.isFile && isMp4ExportInputFileName(childName) -> {
                        result += CacheMediaFile(
                            name = childName,
                            relativePath = childRelativePath,
                            size = runCatching { child.length() }.getOrDefault(0L)
                                .coerceAtLeast(0L),
                            input = MediaInputRef.ContentUri(child.uri.toString())
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

    private fun selectEntryFile(files: List<DocumentFile>): DocumentFile? {
        return entryFileSelector.select(files.filter { it.isFile }) { it.name.orEmpty() }
    }

    private fun safeListFiles(directory: DocumentFile): List<DocumentFile> {
        return runCatching { directory.listFiles().toList() }
            .getOrDefault(emptyList())
    }

    private fun joinPath(parent: String, child: String): String {
        return listOf(parent, child).filter { it.isNotEmpty() }.joinToString("/")
    }

    private data class Node(
        val file: DocumentFile,
        val relativePath: String
    )
}
