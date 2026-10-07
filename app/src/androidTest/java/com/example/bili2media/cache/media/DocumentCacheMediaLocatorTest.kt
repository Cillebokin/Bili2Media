package com.example.bili2media.cache.media

import androidx.documentfile.provider.FakeDocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bili2media.cache.model.CacheEntryLocation
import com.example.bili2media.cache.model.MediaInputRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentCacheMediaLocatorTest {
    @Test
    fun locate_usesInjectedChildListerForRootAndNestedDirectories() {
        val audio = FakeDocumentFile.file("audio", "audio.m4s", 2)
        val mediaDirectory = FakeDocumentFile.directory("media", "80")
        val candidate = FakeDocumentFile.directory("candidate", "cache")
        val listedDirectories = mutableListOf<String>()
        val childrenByDirectory = mapOf(
            candidate.uri.toString() to listOf(mediaDirectory),
            mediaDirectory.uri.toString() to listOf(audio)
        )
        val locator = DocumentCacheMediaLocator(
            documentResolver = { uri ->
                candidate.takeIf { uri == candidate.uri.toString() }
            },
            childLister = DocumentChildLister { directory ->
                listedDirectories += directory.uri.toString()
                childrenByDirectory[directory.uri.toString()].orEmpty()
            }
        )

        val files = locator.locate(
            CacheEntryLocation.DocumentDirectory(candidate.uri.toString())
        )

        assertEquals(
            listOf(candidate.uri.toString(), mediaDirectory.uri.toString()),
            listedDirectories
        )
        assertEquals(listOf("80/audio.m4s"), files.map { it.relativePath })
        assertEquals(
            audio.uri.toString(),
            (files.single().input as MediaInputRef.ContentUri).uri
        )
    }

    @Test
    fun locate_returnsContentUrisAndSkipsNestedCacheRoots() {
        val audio = FakeDocumentFile.file("audio", "audio.m4s", 2)
        val video = FakeDocumentFile.file("video", "video.M4S", 3)
        val existing = FakeDocumentFile.file("existing", "existing.mp4", 4)
        val existingAudio = FakeDocumentFile.file("existing-audio", "existing.m4a", 4)
        val mediaDirectory = FakeDocumentFile.directory("media", "80")
            .add(video)
            .add(audio)
        val nested = FakeDocumentFile.directory("nested", "nested")
            .add(FakeDocumentFile.file("nested-entry", "entry.json", 1))
            .add(FakeDocumentFile.file("nested-video", "video.m4s", 5))
        val candidate = FakeDocumentFile.directory("candidate", "cache")
            .add(FakeDocumentFile.file("entry", "entry.json", 1))
            .add(mediaDirectory)
            .add(existing)
            .add(existingAudio)
            .add(FakeDocumentFile.file("ignored", "ignored.flv", 6))
            .add(nested)
        val locator = DocumentCacheMediaLocator(
            documentResolver = { uri ->
                candidate.takeIf { uri == candidate.uri.toString() }
            }
        )

        val files = locator.locate(
            CacheEntryLocation.DocumentDirectory(candidate.uri.toString())
        )

        assertEquals(
            listOf("80/audio.m4s", "80/video.M4S", "existing.m4a", "existing.mp4"),
            files.map { it.relativePath }
        )
        assertEquals(
            listOf(audio, video, existingAudio, existing).map { it.uri.toString() },
            files.map { (it.input as MediaInputRef.ContentUri).uri }
        )
    }

    @Test
    fun locate_returnsEmptyWhenDocumentCannotBeResolved() {
        val locator = DocumentCacheMediaLocator(documentResolver = { null })

        assertTrue(
            locator.locate(
                CacheEntryLocation.DocumentDirectory("content://bili2media.test/missing")
            ).isEmpty()
        )
    }
}
