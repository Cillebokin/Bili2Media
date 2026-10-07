package com.example.bili2media.export.work

import java.util.concurrent.atomic.AtomicLong

object ExportRequestOrder {
    private val lastRequestOrder = AtomicLong(0L)

    fun next(): Long {
        return lastRequestOrder.updateAndGet { previous ->
            maxOf(System.currentTimeMillis(), previous + 1L)
        }
    }
}
