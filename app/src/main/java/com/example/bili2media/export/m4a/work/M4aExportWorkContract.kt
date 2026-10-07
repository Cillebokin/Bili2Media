package com.example.bili2media.export.m4a.work

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import com.example.bili2media.cache.model.CacheEntryLocation
import com.example.bili2media.export.m4a.usecase.M4aExportRequest
import com.example.bili2media.export.work.ExportRequestOrder

object M4aExportWorkContract {
    const val TAG_ALL_EXPORTS = "bili2media:m4a-export"
    private const val UNIQUE_WORK_PREFIX = "bili2media:m4a-export:"
    private const val ENTRY_TAG_PREFIX = "bili2media:m4a-export-entry:"
    private const val REQUEST_ORDER_TAG_PREFIX = "bili2media:m4a-export-order:"

    const val KEY_ENTRY_ID = "entry_id"
    const val KEY_TITLE = "title"
    const val KEY_LOCATION_TYPE = "location_type"
    const val KEY_LOCATION_VALUE = "location_value"
    const val KEY_PROGRESS = "progress"
    const val KEY_PHASE = "phase"
    const val KEY_OUTPUT_URI = "output_uri"
    const val KEY_ERROR_CODE = "error_code"

    const val PHASE_ANALYZING = "analyzing"
    const val PHASE_EXPORTING = "exporting"

    val existingWorkPolicy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP

    fun encodeRequest(request: M4aExportRequest): Data {
        val (locationType, locationValue) = when (val location = request.location) {
            is CacheEntryLocation.FileDirectory -> LOCATION_FILE to location.path
            is CacheEntryLocation.DocumentDirectory -> LOCATION_DOCUMENT to location.uri
        }
        return Data.Builder()
            .putString(KEY_ENTRY_ID, request.entryId)
            .putString(KEY_TITLE, request.title)
            .putString(KEY_LOCATION_TYPE, locationType)
            .putString(KEY_LOCATION_VALUE, locationValue)
            .build()
    }

    fun decodeRequest(data: Data): M4aExportRequest? {
        val entryId = data.getString(KEY_ENTRY_ID)?.takeIf { it.isNotBlank() } ?: return null
        val title = data.getString(KEY_TITLE)?.takeIf { it.isNotBlank() } ?: return null
        val locationValue = data.getString(KEY_LOCATION_VALUE)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val location = when (data.getString(KEY_LOCATION_TYPE)) {
            LOCATION_FILE -> CacheEntryLocation.FileDirectory(locationValue)
            LOCATION_DOCUMENT -> CacheEntryLocation.DocumentDirectory(locationValue)
            else -> return null
        }
        return M4aExportRequest(
            entryId = entryId,
            title = title,
            location = location
        )
    }

    fun uniqueWorkName(entryId: String): String = "$UNIQUE_WORK_PREFIX$entryId"

    fun entryTag(entryId: String): String = "$ENTRY_TAG_PREFIX$entryId"

    fun entryIdFromTags(tags: Set<String>): String? {
        return tags.asSequence()
            .filter { it.startsWith(ENTRY_TAG_PREFIX) }
            .map { it.removePrefix(ENTRY_TAG_PREFIX) }
            .filter { it.isNotBlank() }
            .sorted()
            .firstOrNull()
    }

    fun requestOrderFromTags(tags: Set<String>): Long? {
        return tags.asSequence()
            .filter { it.startsWith(REQUEST_ORDER_TAG_PREFIX) }
            .mapNotNull { it.removePrefix(REQUEST_ORDER_TAG_PREFIX).toLongOrNull() }
            .maxOrNull()
    }

    fun createWorkRequest(
        request: M4aExportRequest,
        requestOrder: Long = ExportRequestOrder.next()
    ): OneTimeWorkRequest {
        return OneTimeWorkRequestBuilder<M4aExportWorker>()
            .setInputData(encodeRequest(request))
            .addTag(TAG_ALL_EXPORTS)
            .addTag(entryTag(request.entryId))
            .addTag("$REQUEST_ORDER_TAG_PREFIX$requestOrder")
            .build()
    }

    private const val LOCATION_FILE = "file"
    private const val LOCATION_DOCUMENT = "document"
}
