package com.example.bili2media.ui.export

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.work.WorkManager
import com.example.bili2media.cache.model.BiliCacheEntry
import com.example.bili2media.export.usecase.Mp4ExportRequest
import com.example.bili2media.export.work.Mp4ExportWorkContract

class Mp4ExportViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val workManager = WorkManager.getInstance(application)
    private val stateMapper = Mp4ExportStateMapper()

    val states: LiveData<Map<String, Mp4ExportUiState>> = MediatorLiveData<
        Map<String, Mp4ExportUiState>
    >().apply {
        addSource(
            workManager.getWorkInfosByTagLiveData(Mp4ExportWorkContract.TAG_ALL_EXPORTS)
        ) { workInfos ->
            value = stateMapper.map(workInfos.orEmpty())
        }
    }

    fun enqueue(entry: BiliCacheEntry) {
        val request = Mp4ExportRequest(
            entryId = entry.id,
            title = entry.title,
            location = entry.location
        )
        workManager.enqueueUniqueWork(
            Mp4ExportWorkContract.uniqueWorkName(entry.id),
            Mp4ExportWorkContract.existingWorkPolicy,
            Mp4ExportWorkContract.createWorkRequest(request)
        )
    }

    fun cancel(entryId: String) {
        workManager.cancelUniqueWork(Mp4ExportWorkContract.uniqueWorkName(entryId))
    }
}
