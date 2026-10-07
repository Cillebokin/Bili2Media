package com.example.bili2media.export.mp3.output

interface Mp3OutputStore {
    fun create(title: String): Mp3PendingOutput
    fun commit(output: Mp3PendingOutput)
    fun abandon(output: Mp3PendingOutput)
}
