package com.example.bili2media.export.output

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bili2media.testmedia.TestMediaFixtureFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStoreMp4OutputStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val contentResolver = context.contentResolver
    private val store = MediaStoreMp4OutputStore(
        context = context,
        timestampProvider = { 1L }
    )
    private val fixtures = TestMediaFixtureFactory(context)

    @Test
    fun create_returnsWritablePendingOutput() {
        val output = store.create("Writable-${System.nanoTime()}")
        val uri = Uri.parse(output.uri)
        try {
            val stream = contentResolver.openOutputStream(uri, "w")
            assertNotNull(stream)
            stream!!.use { it.write(byteArrayOf(1, 2, 3)) }
            assertEquals(1, readPendingState(uri))
        } finally {
            contentResolver.delete(uri, null, null)
        }
    }

    @Test
    fun commit_publishesPendingRow() {
        val output = store.create("Commit-${System.nanoTime()}")
        val uri = Uri.parse(output.uri)
        val source = fixtures.createCombinedMp4()
        try {
            contentResolver.openOutputStream(uri, "w")!!.use { outputStream ->
                source.inputStream().use { inputStream -> inputStream.copyTo(outputStream) }
            }
            assertEquals(1, readPendingState(uri))

            store.commit(output)

            assertEquals(0, readPendingState(uri))
        } finally {
            contentResolver.delete(uri, null, null)
            source.delete()
        }
    }

    @Test
    fun create_incrementsDuplicateNamesInFixedOutputDirectory() {
        val title = "Duplicate-${System.nanoTime()}"
        val first = store.create(title)
        val second = store.create(title)
        try {
            assertEquals("$title.mp4", first.displayName)
            assertEquals("$title (1).mp4", second.displayName)
        } finally {
            contentResolver.delete(Uri.parse(first.uri), null, null)
            contentResolver.delete(Uri.parse(second.uri), null, null)
        }
    }

    @Test
    fun abandon_deletesPendingRow() {
        val output = store.create("Abandon-${System.nanoTime()}")
        val uri = Uri.parse(output.uri)

        store.abandon(output)

        assertNull(readPendingState(uri))
    }

    private fun readPendingState(uri: Uri): Int? {
        return contentResolver.query(
            uri,
            arrayOf(MediaStore.Downloads.IS_PENDING),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Downloads.IS_PENDING))
            }
        }
    }
}
