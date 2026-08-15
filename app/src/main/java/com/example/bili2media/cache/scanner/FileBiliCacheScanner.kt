package com.example.bili2media.cache.scanner

import com.example.bili2media.cache.cover.BiliCacheCoverResolver
import com.example.bili2media.cache.model.BiliCacheEntry
import com.example.bili2media.cache.model.CacheEntryLocation
import com.example.bili2media.cache.policy.BiliCacheEntryFileSelector
import java.io.File
import java.util.ArrayDeque
import java.util.Locale

class FileBiliCacheScanner(
    private val coverResolver: BiliCacheCoverResolver = BiliCacheCoverResolver(),
    private val entryFileSelector: BiliCacheEntryFileSelector = BiliCacheEntryFileSelector(),
    private val entryFactory: BiliCacheEntryFactory = BiliCacheEntryFactory(
        coverResolver = coverResolver
    )
) {
    fun scan(root: File): List<BiliCacheEntry> {
        if (!root.isDirectory) {
            return emptyList()
        }

        val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: return emptyList()
        val candidates = findCandidates(canonicalRoot)

        return candidates.map { candidate ->
            val relativePath = runCatching {
                candidate.directory.relativeTo(canonicalRoot).invariantSeparatorsPath
            }.getOrDefault(candidate.directory.name)
                .ifBlank { candidate.directory.name }
            val jsonText = runCatching {
                candidate.entryFile.readText()
            }.getOrDefault("")
            val assets = collectAssets(candidate.directory)

            entryFactory.create(
                fallbackName = candidate.directory.name.ifBlank { relativePath },
                relativePath = relativePath,
                location = CacheEntryLocation.FileDirectory(candidate.directory.path),
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

    private fun findCandidates(root: File): List<Candidate> {
        val result = mutableListOf<Candidate>()
        val pending = ArrayDeque<File>().apply { add(root) }
        val visited = mutableSetOf<String>()

        while (pending.isNotEmpty()) {
            val directory = pending.removeLast()
            val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: continue
            if (!visited.add(canonicalDirectory.path) || !canonicalDirectory.isDirectory) {
                continue
            }

            val children = safeListFiles(canonicalDirectory)
            val entryFile = selectEntryFile(children)
            if (entryFile != null) {
                result += Candidate(canonicalDirectory, entryFile)
            }
            children.asSequence()
                .filter { it.isDirectory }
                .forEach(pending::add)
        }

        return result
    }

    private fun collectAssets(candidate: File): CandidateAssets {
        val mediaFiles = mutableListOf<BiliCacheMedia>()
        val coverFiles = mutableListOf<File>()
        val candidatePath = runCatching { candidate.canonicalPath }.getOrDefault(candidate.path)
        val pending = ArrayDeque<File>().apply { add(candidate) }
        val visited = mutableSetOf<String>()

        while (pending.isNotEmpty()) {
            val directory = pending.removeLast()
            val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: continue
            if (!visited.add(canonicalDirectory.path) || !canonicalDirectory.isDirectory) {
                continue
            }

            val children = safeListFiles(canonicalDirectory)
            val isNestedCandidate = canonicalDirectory.path != candidatePath &&
                selectEntryFile(children) != null
            if (isNestedCandidate) {
                continue
            }

            children.forEach { child ->
                when {
                    child.isDirectory -> pending.add(child)
                    child.isFile && BiliCacheMedia.isSupportedFileName(child.name) -> {
                        mediaFiles += BiliCacheMedia(
                            name = child.name,
                            size = runCatching { child.length() }.getOrDefault(0L)
                        )
                    }
                    child.isFile && coverResolver.isLocalCoverFileName(child.name) -> {
                        coverFiles += child
                    }
                }
            }
        }

        val localCoverUri = coverFiles.minWithOrNull(
            compareBy<File> { relativePath(candidate, it).lowercase(Locale.ROOT) }
                .thenBy { relativePath(candidate, it) }
        )?.toURI()?.toString()

        return CandidateAssets(
            mediaFiles = mediaFiles,
            localCoverUri = localCoverUri
        )
    }

    private fun relativePath(root: File, child: File): String {
        return runCatching { child.relativeTo(root).invariantSeparatorsPath }
            .getOrDefault(child.path)
    }

    private fun safeListFiles(directory: File): List<File> {
        return runCatching { directory.listFiles()?.toList().orEmpty() }
            .getOrDefault(emptyList())
    }

    private fun selectEntryFile(files: List<File>): File? {
        return entryFileSelector.select(files.filter { it.isFile }) { it.name }
    }

    private data class Candidate(
        val directory: File,
        val entryFile: File
    )

    private data class CandidateAssets(
        val mediaFiles: List<BiliCacheMedia>,
        val localCoverUri: String?
    )

}
