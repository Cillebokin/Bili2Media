package com.example.bili2media.ui.export.m4a

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.work.WorkManager
import com.example.bili2media.cache.model.BiliCacheEntry
import com.example.bili2media.export.m4a.usecase.M4aExportRequest
import com.example.bili2media.export.m4a.work.M4aExportWorkContract
import com.example.bili2media.export.output.CacheEntryExportTitleResolver

class M4aExportViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val workManager = WorkManager.getInstance(application)
    private val stateMapper = M4aExportStateMapper()
    private val exportTitleResolver = CacheEntryExportTitleResolver()

    val states: LiveData<Map<String, M4aExportUiState>> = MediatorLiveData<
        Map<String, M4aExportUiState>
    >().apply {
        addSource(
            workManager.getWorkInfosByTagLiveData(M4aExportWorkContract.TAG_ALL_EXPORTS)
        ) { workInfos ->
            value = stateMapper.map(workInfos.orEmpty())
        }
    }

    fun enqueue(entry: BiliCacheEntry) {
        val request = M4aExportRequest(
            entryId = entry.id,
            title = exportTitleResolver.resolve(entry.title, entry.subtitle),
            location = entry.location
        )
        workManager.enqueueUniqueWork(
            M4aExportWorkContract.uniqueWorkName(entry.id),
            M4aExportWorkContract.existingWorkPolicy,
            M4aExportWorkContract.createWorkRequest(request)
        )
    }

    fun cancel(entryId: String) {
        workManager.cancelUniqueWork(M4aExportWorkContract.uniqueWorkName(entryId))
    }
}
