package com.example.bili2media.export.mp3.work

import android.content.Context
import com.example.bili2media.AppSettings
import com.example.bili2media.cache.media.AndroidCacheMediaLocator
import com.example.bili2media.export.mp3.engine.AndroidMp3ExportEngine
import com.example.bili2media.export.mp3.output.MediaStoreMp3OutputStore
import com.example.bili2media.export.mp3.planner.Mp3ExportPlanner
import com.example.bili2media.export.mp3.usecase.Mp3ExportUseCase
import com.example.bili2media.media.probe.AndroidMediaExtractorProbe

object Mp3ExportUseCaseFactory {
    fun create(context: Context): Mp3ExportUseCase {
        val applicationContext = context.applicationContext
        return Mp3ExportUseCase(
            mediaLocator = AndroidCacheMediaLocator(applicationContext),
            mediaProbe = AndroidMediaExtractorProbe(applicationContext),
            planner = Mp3ExportPlanner(),
            outputStore = MediaStoreMp3OutputStore(applicationContext),
            engine = AndroidMp3ExportEngine(
                applicationContext,
                AppSettings.getMp3BitrateKbps(applicationContext)
            )
        )
    }
}
