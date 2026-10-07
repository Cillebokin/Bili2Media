package com.example.bili2media.ui.export.mp3

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.work.WorkManager
import com.example.bili2media.cache.model.BiliCacheEntry
import com.example.bili2media.export.mp3.usecase.Mp3ExportRequest
import com.example.bili2media.export.mp3.work.Mp3ExportWorkContract
import com.example.bili2media.export.output.CacheEntryExportTitleResolver

class Mp3ExportViewModel(application: Application) : AndroidViewModel(application) {
    private val workManager = WorkManager.getInstance(application)
    private val stateMapper = Mp3ExportStateMapper()
    private val exportTitleResolver = CacheEntryExportTitleResolver()

    val states: LiveData<Map<String, Mp3ExportUiState>> = MediatorLiveData<
        Map<String, Mp3ExportUiState>
    >().apply {
        addSource(workManager.getWorkInfosByTagLiveData(Mp3ExportWorkContract.TAG_ALL_EXPORTS)) {
            value = stateMapper.map(it.orEmpty())
        }
    }

    fun enqueue(entry: BiliCacheEntry) {
        val request = Mp3ExportRequest(
            entryId = entry.id,
            title = exportTitleResolver.resolve(entry.title, entry.subtitle),
            location = entry.location
        )
        workManager.enqueueUniqueWork(
            Mp3ExportWorkContract.uniqueWorkName(entry.id),
            Mp3ExportWorkContract.existingWorkPolicy,
            Mp3ExportWorkContract.createWorkRequest(request)
        )
    }

    fun cancel(entryId: String) {
        workManager.cancelUniqueWork(Mp3ExportWorkContract.uniqueWorkName(entryId))
    }
}
