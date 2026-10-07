package com.example.bili2media.export.m4a.output

interface M4aOutputStore {
    fun create(title: String): M4aPendingOutput
    fun commit(output: M4aPendingOutput)
    fun abandon(output: M4aPendingOutput)
}
