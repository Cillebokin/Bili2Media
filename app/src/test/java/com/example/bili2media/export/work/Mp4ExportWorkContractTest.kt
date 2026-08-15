package com.example.bili2media.export.work

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import com.example.bili2media.cache.model.CacheEntryLocation
import com.example.bili2media.export.usecase.Mp4ExportRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Mp4ExportWorkContractTest {
    @Test
    fun request_roundTripsFileAndDocumentLocations() {
        val requests = listOf(
            Mp4ExportRequest(
                entryId = "file-entry",
                title = "File title",
                location = CacheEntryLocation.FileDirectory("/storage/cache/file-entry")
            ),
            Mp4ExportRequest(
                entryId = "document-entry",
                title = "Document title",
                location = CacheEntryLocation.DocumentDirectory(
                    "content://provider/tree/document-entry"
                )
            )
        )

        requests.forEach { request ->
            assertEquals(
                request,
                Mp4ExportWorkContract.decodeRequest(
                    Mp4ExportWorkContract.encodeRequest(request)
                )
            )
        }
    }

    @Test
    fun uniqueNameAndEntryTagAreStablePerEntry() {
        assertEquals(
            "bili2media:mp4-export:file-entry",
            Mp4ExportWorkContract.uniqueWorkName("file-entry")
        )
        assertEquals(
            Mp4ExportWorkContract.uniqueWorkName("file-entry"),
            Mp4ExportWorkContract.uniqueWorkName("file-entry")
        )
        assertEquals(
            "bili2media:mp4-export-entry:file-entry",
            Mp4ExportWorkContract.entryTag("file-entry")
        )
        assertEquals(ExistingWorkPolicy.KEEP, Mp4ExportWorkContract.existingWorkPolicy)
    }

    @Test
    fun workRequest_containsGlobalAndEntryTags() {
        val request = Mp4ExportRequest(
            entryId = "entry-42",
            title = "Episode",
            location = CacheEntryLocation.FileDirectory("/cache/entry-42")
        )

        val workRequest = Mp4ExportWorkContract.createWorkRequest(
            request = request,
            requestOrder = 42L
        )

        assertTrue(workRequest.tags.contains(Mp4ExportWorkContract.TAG_ALL_EXPORTS))
        assertTrue(workRequest.tags.contains(Mp4ExportWorkContract.entryTag("entry-42")))
        assertEquals(
            "entry-42",
            Mp4ExportWorkContract.entryIdFromTags(workRequest.tags)
        )
        assertEquals(
            42L,
            Mp4ExportWorkContract.requestOrderFromTags(workRequest.tags)
        )
        assertEquals(request, Mp4ExportWorkContract.decodeRequest(workRequest.workSpec.input))
    }

    @Test
    fun decodeRequest_returnsNullForMissingOrUnknownLocationData() {
        assertNull(Mp4ExportWorkContract.decodeRequest(Data.EMPTY))
        assertNull(
            Mp4ExportWorkContract.decodeRequest(
                Data.Builder()
                    .putString(Mp4ExportWorkContract.KEY_ENTRY_ID, "entry")
                    .putString(Mp4ExportWorkContract.KEY_TITLE, "title")
                    .putString(Mp4ExportWorkContract.KEY_LOCATION_TYPE, "unknown")
                    .putString(Mp4ExportWorkContract.KEY_LOCATION_VALUE, "/cache")
                    .build()
            )
        )
        assertNull(
            Mp4ExportWorkContract.decodeRequest(
                Data.Builder()
                    .putString(Mp4ExportWorkContract.KEY_ENTRY_ID, " ")
                    .putString(Mp4ExportWorkContract.KEY_TITLE, "title")
                    .putString(Mp4ExportWorkContract.KEY_LOCATION_TYPE, "file")
                    .putString(Mp4ExportWorkContract.KEY_LOCATION_VALUE, "/cache")
                    .build()
            )
        )
    }
}
