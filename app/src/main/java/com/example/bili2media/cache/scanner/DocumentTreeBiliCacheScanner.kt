package com.example.bili2media.cache.scanner

import android.content.ContentResolver
import androidx.documentfile.provider.DocumentFile
import com.example.bili2media.cache.cover.BiliCacheCoverResolver
import com.example.bili2media.cache.model.BiliCacheEntry
import com.example.bili2media.cache.model.CacheEntryLocation
import com.example.bili2media.cache.policy.BiliCacheEntryFileSelector
import java.util.ArrayDeque
import java.util.Locale

class DocumentTreeBiliCacheScanner(
    private val entryJsonReader: DocumentEntryJsonReader,
    private val coverResolver: BiliCacheCoverResolver = BiliCacheCoverResolver(),
    private val entryFileSelector: BiliCacheEntryFileSelector = BiliCacheEntryFileSelector(),
    private val entryFactory: BiliCacheEntryFactory = BiliCacheEntryFactory(
        coverResolver = coverResolver
    )
) {
    constructor(contentResolver: ContentResolver) : this(
        entryJsonReader = ContentResolverDocumentEntryJsonReader(contentResolver)
    )

    fun scan(root: DocumentFile): List<BiliCacheEntry> {
        if (!root.isDirectory || !root.canRead()) {
            return emptyList()
        }

        return findCandidates(root).map { candidate ->
            val jsonText = runCatching {
                entryJsonReader.read(candidate.entryFile)
            }.getOrDefault("")
            val assets = collectAssets(candidate.directory)

            entryFactory.create(
                fallbackName = candidate.directory.name.orEmpty()
                    .ifBlank { candidate.relativePath },
                relativePath = candidate.relativePath,
                location = CacheEntryLocation.DocumentDirectory(
                    candidate.directory.uri.toString()
                ),
                jsonText = jsonText,
                mediaFiles = assets.mediaFiles,
                localCoverUri = assets.localCoverUri
            )
        }.sortedWith(
            compareBy<BiliCacheEntry> { it.title.lowercase(Locale.ROOT) }
                .thenBy { it.relativePath.lowercase(Locale.ROOT) }
                .thenBy { it.relativePath }
        )
    }

    private fun findCandidates(root: DocumentFile): List<Candidate> {
        val rootName = root.name.orEmpty().ifBlank { ROOT_FALLBACK_NAME }
        val pending = ArrayDeque<DocumentNode>().apply {
            add(DocumentNode(root, rootName))
        }
        val visited = mutableSetOf<String>()
        val result = mutableListOf<Candidate>()

        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            if (!visited.add(node.file.uri.toString()) || !node.file.isDirectory) {
                continue
            }

            val children = safeListFiles(node.file)
            val entryFile = selectEntryFile(children)
            if (entryFile != null) {
                result += Candidate(node.file, entryFile, node.relativePath)
            }

            children.asSequence()
                .filter { it.isDirectory }
                .forEach { child ->
                    val childName = child.name.orEmpty().ifBlank { child.uri.lastPathSegment.orEmpty() }
                    pending.add(
                        DocumentNode(
                            file = child,
                            relativePath = joinPath(node.relativePath, childName)
                        )
                    )
                }
        }

        return result
    }

    private fun collectAssets(candidate: DocumentFile): CandidateAssets {
        val mediaFiles = mutableListOf<BiliCacheMedia>()
        val covers = mutableListOf<CoverCandidate>()
        val candidateUri = candidate.uri.toString()
        val pending = ArrayDeque<DocumentNode>().apply {
            add(DocumentNode(candidate, ""))
        }
        val visited = mutableSetOf<String>()

        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            val directory = node.file
            if (!visited.add(directory.uri.toString()) || !directory.isDirectory) {
                continue
            }

            val children = safeListFiles(directory)
            val isNestedCandidate = directory.uri.toString() != candidateUri &&
                selectEntryFile(children) != null
            if (isNestedCandidate) {
                continue
            }

            children.forEach { child ->
                val childName = child.name.orEmpty().ifBlank { child.uri.lastPathSegment.orEmpty() }
                val childRelativePath = joinPath(node.relativePath, childName)
                when {
                    child.isDirectory -> pending.add(DocumentNode(child, childRelativePath))
                    child.isFile && BiliCacheMedia.isSupportedFileName(child.name.orEmpty()) -> {
                        mediaFiles += BiliCacheMedia(
                            name = child.name.orEmpty(),
                            size = runCatching { child.length() }.getOrDefault(0L)
                        )
                    }
                    child.isFile && coverResolver.isLocalCoverFileName(child.name.orEmpty()) -> {
                        covers += CoverCandidate(
                            relativePath = childRelativePath,
                            uri = child.uri.toString()
                        )
                    }
                }
            }
        }

        val localCoverUri = covers.minWithOrNull(
            compareBy<CoverCandidate> { it.relativePath.lowercase(Locale.ROOT) }
                .thenBy { it.relativePath }
                .thenBy { it.uri }
        )?.uri

        return CandidateAssets(
            mediaFiles = mediaFiles,
            localCoverUri = localCoverUri
        )
    }

    private fun safeListFiles(directory: DocumentFile): List<DocumentFile> {
        return runCatching { directory.listFiles().toList() }
            .getOrDefault(emptyList())
    }

    private fun selectEntryFile(files: List<DocumentFile>): DocumentFile? {
        return entryFileSelector.select(files.filter { it.isFile }) { it.name.orEmpty() }
    }

    private fun joinPath(parent: String, child: String): String {
        return listOf(parent, child)
            .filter { it.isNotBlank() }
            .joinToString("/")
    }

    private data class DocumentNode(
        val file: DocumentFile,
        val relativePath: String
    )

    private data class Candidate(
        val directory: DocumentFile,
        val entryFile: DocumentFile,
        val relativePath: String
    )

    private data class CandidateAssets(
        val mediaFiles: List<BiliCacheMedia>,
        val localCoverUri: String?
    )

    private data class CoverCandidate(
        val relativePath: String,
        val uri: String
    )

    private companion object {
        const val ROOT_FALLBACK_NAME = "selected"
    }
}
