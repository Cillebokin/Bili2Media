package com.example.bili2media.export.mp3.engine

import com.example.bili2media.export.mp3.model.Mp3ExportPlan

interface Mp3EngineListener {
    fun onProgress(percent: Int)
    fun isCancelled(): Boolean
}

interface Mp3ExportEngine {
    fun export(plan: Mp3ExportPlan, outputUri: String, listener: Mp3EngineListener): Mp3EngineResult
}
