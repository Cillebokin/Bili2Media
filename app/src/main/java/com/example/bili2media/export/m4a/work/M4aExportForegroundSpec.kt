package com.example.bili2media.export.m4a.work

import android.content.pm.ServiceInfo
import java.util.UUID

internal data class M4aExportForegroundSpec(
    val channelId: String,
    val notificationId: Int,
    val foregroundServiceType: Int,
    val cancelWorkId: UUID
) {
    companion object {
        fun forWork(workId: UUID): M4aExportForegroundSpec {
            return M4aExportForegroundSpec(
                channelId = CHANNEL_ID,
                notificationId = (workId.hashCode() and Int.MAX_VALUE).coerceAtLeast(1),
                foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                cancelWorkId = workId
            )
        }

        const val CHANNEL_ID = "m4a_exports"
    }
}
