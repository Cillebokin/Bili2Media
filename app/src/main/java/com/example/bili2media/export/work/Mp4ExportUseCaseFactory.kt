package com.example.bili2media.export.work

import android.content.Context
import com.example.bili2media.cache.media.AndroidCacheMediaLocator
import com.example.bili2media.export.engine.AndroidMp4ExportEngine
import com.example.bili2media.export.output.MediaStoreMp4OutputStore
import com.example.bili2media.export.planner.Mp4ExportPlanner
import com.example.bili2media.export.usecase.Mp4ExportUseCase
import com.example.bili2media.media.probe.AndroidMediaExtractorProbe

object Mp4ExportUseCaseFactory {
    fun create(context: Context): Mp4ExportUseCase {
        val applicationContext = context.applicationContext
        return Mp4ExportUseCase(
            mediaLocator = AndroidCacheMediaLocator(applicationContext),
            mediaProbe = AndroidMediaExtractorProbe(applicationContext),
            planner = Mp4ExportPlanner(),
            outputStore = MediaStoreMp4OutputStore(applicationContext),
            engine = AndroidMp4ExportEngine(applicationContext)
        )
    }
}
