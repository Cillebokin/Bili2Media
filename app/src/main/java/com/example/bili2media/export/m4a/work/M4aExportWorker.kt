package com.example.bili2media.export.m4a.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.bili2media.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class M4aExportWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            M4aExportWorkerCoordinator(
                export = { request, listener ->
                    M4aExportUseCaseFactory.create(applicationContext).execute(request, listener)
                },
                isStopped = { isStopped },
                publishProgress = ::publishProgress
            ).execute(inputData) {
                setForeground(createForegroundInfo(progress = null))
            }
        }
    }

    private fun publishProgress(progressData: Data) {
        val progress = if (
            progressData.getString(M4aExportWorkContract.KEY_PHASE) ==
                M4aExportWorkContract.PHASE_ANALYZING
        ) {
            null
        } else {
            progressData.getInt(M4aExportWorkContract.KEY_PROGRESS, 0).coerceIn(0, 100)
        }
        setProgressAsync(
            progressData
        )
        setForegroundAsync(createForegroundInfo(progress))
    }

    private fun createForegroundInfo(progress: Int?): ForegroundInfo {
        val foregroundSpec = M4aExportForegroundSpec.forWork(id)
        createNotificationChannel(foregroundSpec)
        val text = if (progress == null) {
            applicationContext.getString(R.string.m4a_export_notification_analyzing)
        } else {
            applicationContext.getString(R.string.m4a_export_notification_progress, progress)
        }
        val cancelIntent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(foregroundSpec.cancelWorkId)
        val notification = NotificationCompat.Builder(applicationContext, foregroundSpec.channelId)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(
                applicationContext.getString(R.string.m4a_export_notification_title)
            )
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress ?: 0, progress == null)
            .addAction(
                android.R.drawable.ic_delete,
                applicationContext.getString(R.string.m4a_export_notification_cancel),
                cancelIntent
            )
            .build()
        return ForegroundInfo(
            foregroundSpec.notificationId,
            notification,
            foregroundSpec.foregroundServiceType
        )
    }

    private fun createNotificationChannel(foregroundSpec: M4aExportForegroundSpec) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                foregroundSpec.channelId,
                applicationContext.getString(R.string.m4a_export_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }
}
