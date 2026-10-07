package com.example.bili2media.export.m4a.work

import android.content.Context
import com.example.bili2media.cache.media.AndroidCacheMediaLocator
import com.example.bili2media.export.m4a.engine.AndroidM4aExportEngine
import com.example.bili2media.export.m4a.output.MediaStoreM4aOutputStore
import com.example.bili2media.export.m4a.planner.M4aExportPlanner
import com.example.bili2media.export.m4a.usecase.M4aExportUseCase
import com.example.bili2media.media.probe.AndroidMediaExtractorProbe

object M4aExportUseCaseFactory {
    fun create(context: Context): M4aExportUseCase {
        val applicationContext = context.applicationContext
        return M4aExportUseCase(
            mediaLocator = AndroidCacheMediaLocator(applicationContext),
            mediaProbe = AndroidMediaExtractorProbe(applicationContext),
            planner = M4aExportPlanner(),
            outputStore = MediaStoreM4aOutputStore(applicationContext),
            engine = AndroidM4aExportEngine(applicationContext)
        )
    }
}
