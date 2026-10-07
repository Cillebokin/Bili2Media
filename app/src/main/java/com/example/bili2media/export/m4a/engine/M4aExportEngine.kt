package com.example.bili2media.export.m4a.engine

import com.example.bili2media.export.m4a.model.M4aExportPlan

interface M4aEngineListener {
    fun onProgress(percent: Int)

    fun isCancelled(): Boolean
}

interface M4aExportEngine {
    fun export(
        plan: M4aExportPlan,
        outputUri: String,
        listener: M4aEngineListener
    ): M4aEngineResult
}
