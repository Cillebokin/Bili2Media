package com.example.bili2media.export.engine

import com.example.bili2media.export.model.Mp4ExportPlan

interface Mp4EngineListener {
    fun onProgress(percent: Int)

    fun isCancelled(): Boolean
}

interface Mp4ExportEngine {
    fun export(
        plan: Mp4ExportPlan,
        outputUri: String,
        listener: Mp4EngineListener
    ): Mp4EngineResult
}
