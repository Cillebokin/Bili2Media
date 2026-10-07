package com.example.bili2media.export.m4a.work

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import com.example.bili2media.cache.model.CacheEntryLocation
import com.example.bili2media.export.m4a.usecase.M4aExportRequest
import com.example.bili2media.export.work.Mp4ExportWorkContract
import com.example.bili2media.export.usecase.Mp4ExportRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M4aExportWorkContractTest {
    @Test
    fun request_roundTripsFileAndDocumentLocations() {
        val requests = listOf(
            M4aExportRequest(
                entryId = "file-entry",
                title = "File title",
                location = CacheEntryLocation.FileDirectory("/storage/cache/file-entry")
            ),
            M4aExportRequest(
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
                M4aExportWorkContract.decodeRequest(
                    M4aExportWorkContract.encodeRequest(request)
                )
            )
        }
    }

    @Test
    fun decodeRequest_returnsNullForMissingBlankOrUnknownData() {
        assertNull(M4aExportWorkContract.decodeRequest(Data.EMPTY))
        assertNull(
            M4aExportWorkContract.decodeRequest(
                Data.Builder()
                    .putString(M4aExportWorkContract.KEY_ENTRY_ID, "entry")
                    .putString(M4aExportWorkContract.KEY_LOCATION_TYPE, "file")
                    .putString(M4aExportWorkContract.KEY_LOCATION_VALUE, "/cache")
                    .build()
            )
        )
        assertNull(
            M4aExportWorkContract.decodeRequest(
                Data.Builder()
                    .putString(M4aExportWorkContract.KEY_ENTRY_ID, "entry")
                    .putString(M4aExportWorkContract.KEY_TITLE, "title")
                    .putString(M4aExportWorkContract.KEY_LOCATION_VALUE, "/cache")
                    .build()
            )
        )
        assertNull(
            M4aExportWorkContract.decodeRequest(
                Data.Builder()
                    .putString(M4aExportWorkContract.KEY_ENTRY_ID, "entry")
                    .putString(M4aExportWorkContract.KEY_TITLE, "title")
                    .putString(M4aExportWorkContract.KEY_LOCATION_TYPE, "file")
                    .build()
            )
        )
        assertNull(
            M4aExportWorkContract.decodeRequest(
                Data.Builder()
                    .putString(M4aExportWorkContract.KEY_ENTRY_ID, "entry")
                    .putString(M4aExportWorkContract.KEY_TITLE, "title")
                    .putString(M4aExportWorkContract.KEY_LOCATION_TYPE, "unknown")
                    .putString(M4aExportWorkContract.KEY_LOCATION_VALUE, "/cache")
                    .build()
            )
        )
        assertNull(
            M4aExportWorkContract.decodeRequest(
                Data.Builder()
                    .putString(M4aExportWorkContract.KEY_ENTRY_ID, "entry")
                    .putString(M4aExportWorkContract.KEY_TITLE, "title")
                    .putString(M4aExportWorkContract.KEY_LOCATION_TYPE, " ")
                    .putString(M4aExportWorkContract.KEY_LOCATION_VALUE, "/cache")
                    .build()
            )
        )
        assertNull(
            M4aExportWorkContract.decodeRequest(
                Data.Builder()
                    .putString(M4aExportWorkContract.KEY_ENTRY_ID, " ")
                    .putString(M4aExportWorkContract.KEY_TITLE, "title")
                    .putString(M4aExportWorkContract.KEY_LOCATION_TYPE, "file")
                    .putString(M4aExportWorkContract.KEY_LOCATION_VALUE, "/cache")
                    .build()
            )
        )
        assertNull(
            M4aExportWorkContract.decodeRequest(
                Data.Builder()
                    .putString(M4aExportWorkContract.KEY_ENTRY_ID, "entry")
                    .putString(M4aExportWorkContract.KEY_TITLE, " ")
                    .putString(M4aExportWorkContract.KEY_LOCATION_TYPE, "file")
                    .putString(M4aExportWorkContract.KEY_LOCATION_VALUE, "/cache")
                    .build()
            )
        )
        assertNull(
            M4aExportWorkContract.decodeRequest(
                Data.Builder()
                    .putString(M4aExportWorkContract.KEY_ENTRY_ID, "entry")
                    .putString(M4aExportWorkContract.KEY_TITLE, "title")
                    .putString(M4aExportWorkContract.KEY_LOCATION_TYPE, "file")
                    .putString(M4aExportWorkContract.KEY_LOCATION_VALUE, " ")
                    .build()
            )
        )
    }

    @Test
    fun uniqueNameAndEntryTagRemainM4aSpecific() {
        assertEquals(
            "bili2media:m4a-export:file-entry",
            M4aExportWorkContract.uniqueWorkName("file-entry")
        )
        assertEquals(
            "bili2media:m4a-export-entry:file-entry",
            M4aExportWorkContract.entryTag("file-entry")
        )
        assertEquals(ExistingWorkPolicy.KEEP, M4aExportWorkContract.existingWorkPolicy)
        assertNotEquals(
            Mp4ExportWorkContract.uniqueWorkName("file-entry"),
            M4aExportWorkContract.uniqueWorkName("file-entry")
        )
    }

    @Test
    fun workRequest_containsGlobalEntryAndRequestOrderTags() {
        val request = M4aExportRequest(
            entryId = "entry-42",
            title = "Episode",
            location = CacheEntryLocation.FileDirectory("/cache/entry-42")
        )

        val workRequest = M4aExportWorkContract.createWorkRequest(
            request = request,
            requestOrder = 42L
        )

        assertTrue(workRequest.tags.contains("bili2media:m4a-export"))
        assertTrue(workRequest.tags.contains(M4aExportWorkContract.entryTag("entry-42")))
        assertTrue(workRequest.tags.contains("bili2media:m4a-export-order:42"))
        assertEquals("entry-42", M4aExportWorkContract.entryIdFromTags(workRequest.tags))
        assertEquals(42L, M4aExportWorkContract.requestOrderFromTags(workRequest.tags))
        assertEquals(request, M4aExportWorkContract.decodeRequest(workRequest.workSpec.input))
    }

    @Test
    fun tagParsers_ignoreMp4TagsAndChooseNewestM4aOrder() {
        val tags = setOf(
            "bili2media:mp4-export-entry:mp4-entry",
            "bili2media:mp4-export-order:999",
            "bili2media:m4a-export-entry:m4a-entry",
            "bili2media:m4a-export-order:3",
            "bili2media:m4a-export-order:17",
            "bili2media:m4a-export-order:not-a-number"
        )

        assertEquals("m4a-entry", M4aExportWorkContract.entryIdFromTags(tags))
        assertEquals(17L, M4aExportWorkContract.requestOrderFromTags(tags))
    }

    @Test
    fun defaultRequestOrderIsMonotonicAcrossMp4AndM4aRequests() {
        val location = CacheEntryLocation.FileDirectory("/cache/entry")
        val mp4Request = Mp4ExportWorkContract.createWorkRequest(
            Mp4ExportRequest("mp4-entry", "MP4", location)
        )
        val m4aRequest = M4aExportWorkContract.createWorkRequest(
            M4aExportRequest("m4a-entry", "M4A", location)
        )

        val mp4Order = Mp4ExportWorkContract.requestOrderFromTags(mp4Request.tags)
        val m4aOrder = M4aExportWorkContract.requestOrderFromTags(m4aRequest.tags)
        assertTrue(m4aOrder != null && mp4Order != null && m4aOrder > mp4Order)
    }
}
