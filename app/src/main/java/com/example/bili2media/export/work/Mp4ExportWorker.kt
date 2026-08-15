package com.example.bili2media.export.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.bili2media.R
import com.example.bili2media.export.usecase.Mp4ExportListener
import com.example.bili2media.export.usecase.Mp4ExportOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Mp4ExportWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val request = Mp4ExportWorkContract.decodeRequest(inputData)
            ?: return failure(INVALID_INPUT)
        setForeground(createForegroundInfo(progress = null))

        return withContext(Dispatchers.IO) {
            val useCase = Mp4ExportUseCaseFactory.create(applicationContext)
            val listener = object : Mp4ExportListener {
                override fun onAnalyzing() {
                    publishProgress(request.entryId, null)
                }

                override fun onProgress(percent: Int) {
                    publishProgress(request.entryId, percent.coerceIn(0, 100))
                }

                override fun isCancelled(): Boolean = isStopped
            }

            when (val outcome = useCase.execute(request, listener)) {
                is Mp4ExportOutcome.Success -> Result.success(
                    workDataOf(
                        Mp4ExportWorkContract.KEY_ENTRY_ID to request.entryId,
                        Mp4ExportWorkContract.KEY_OUTPUT_URI to outcome.outputUri
                    )
                )

                is Mp4ExportOutcome.Unsupported -> failure(
                    "UNSUPPORTED_${outcome.reason.name}",
                    request.entryId
                )

                is Mp4ExportOutcome.Failure -> failure(outcome.code, request.entryId)
                Mp4ExportOutcome.Cancelled -> throw CancellationException("MP4 export cancelled")
            }
        }
    }

    private fun publishProgress(entryId: String, progress: Int?) {
        val phase = if (progress == null) {
            Mp4ExportWorkContract.PHASE_ANALYZING
        } else {
            Mp4ExportWorkContract.PHASE_EXPORTING
        }
        setProgressAsync(
            workDataOf(
                Mp4ExportWorkContract.KEY_ENTRY_ID to entryId,
                Mp4ExportWorkContract.KEY_PHASE to phase,
                Mp4ExportWorkContract.KEY_PROGRESS to (progress ?: 0)
            )
        )
        setForegroundAsync(createForegroundInfo(progress))
    }

    private fun createForegroundInfo(progress: Int?): ForegroundInfo {
        createNotificationChannel()
        val text = if (progress == null) {
            applicationContext.getString(R.string.mp4_export_notification_analyzing)
        } else {
            applicationContext.getString(R.string.mp4_export_notification_progress, progress)
        }
        val cancelIntent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(
                applicationContext.getString(R.string.mp4_export_notification_title)
            )
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress ?: 0, progress == null)
            .addAction(
                android.R.drawable.ic_delete,
                applicationContext.getString(R.string.mp4_export_notification_cancel),
                cancelIntent
            )
            .build()
        return ForegroundInfo(
            notificationId(),
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun createNotificationChannel() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                applicationContext.getString(R.string.mp4_export_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun notificationId(): Int {
        return (id.hashCode() and Int.MAX_VALUE).coerceAtLeast(1)
    }

    private fun failure(errorCode: String, entryId: String? = null): Result {
        val data = Data.Builder()
            .putString(Mp4ExportWorkContract.KEY_ERROR_CODE, errorCode)
            .apply {
                if (entryId != null) {
                    putString(Mp4ExportWorkContract.KEY_ENTRY_ID, entryId)
                }
            }
            .build()
        return Result.failure(data)
    }

    private companion object {
        const val NOTIFICATION_CHANNEL_ID = "mp4_exports"
        const val INVALID_INPUT = "INVALID_INPUT"
    }
}
