package com.example.bili2media.export.m4a.output

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
class MediaStoreM4aOutputStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val resolver = context.contentResolver
    private val store = MediaStoreM4aOutputStore(context, timestampProvider = { 1L })
    private val fixtures = TestMediaFixtureFactory(context)

    @Test
    fun create_returnsWritableAudioMp4PendingOutput() {
        val output = store.create("Writable-${System.nanoTime()}")
        val uri = Uri.parse(output.uri)
        try {
            assertNotNull(resolver.openOutputStream(uri, "w")?.use { it.write(byteArrayOf(1)) })
            assertEquals(1, readInt(uri, MediaStore.Downloads.IS_PENDING))
            assertEquals("audio/mp4", readString(uri, MediaStore.Downloads.MIME_TYPE))
        } finally {
            resolver.delete(uri, null, null)
        }
    }

    @Test
    fun commitPublishesAndDuplicateNamesIncrement() {
        val title = "M4a-${System.nanoTime()}"
        val first = store.create(title)
        val second = store.create(title)
        val source = fixtures.createAudioOnlyM4s()
        try {
            resolver.openOutputStream(Uri.parse(first.uri), "w")!!.use { output ->
                source.inputStream().use { it.copyTo(output) }
            }
            store.commit(first)

            assertEquals(0, readInt(Uri.parse(first.uri), MediaStore.Downloads.IS_PENDING))
            assertEquals("$title.m4a", first.displayName)
            assertEquals("$title (1).m4a", second.displayName)
        } finally {
            resolver.delete(Uri.parse(first.uri), null, null)
            resolver.delete(Uri.parse(second.uri), null, null)
            source.delete()
        }
    }

    @Test
    fun abandon_deletesPendingRow() {
        val output = store.create("Abandon-${System.nanoTime()}")
        val uri = Uri.parse(output.uri)

        store.abandon(output)

        assertNull(readInt(uri, MediaStore.Downloads.IS_PENDING))
    }

    private fun readInt(uri: Uri, column: String): Int? {
        return resolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(cursor.getColumnIndexOrThrow(column)) else null
        }
    }

    private fun readString(uri: Uri, column: String): String? {
        return resolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(column)) else null
        }
    }
}
