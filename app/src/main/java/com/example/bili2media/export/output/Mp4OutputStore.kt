package com.example.bili2media.export.output

interface Mp4OutputStore {
    fun create(title: String): Mp4PendingOutput

    fun commit(output: Mp4PendingOutput)

    fun abandon(output: Mp4PendingOutput)
}
