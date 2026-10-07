package com.example.bili2media.export.mp3.engine

object NativeLame {
    init {
        System.loadLibrary("bili2media_lame")
    }

    external fun create(sampleRate: Int, channels: Int, bitrateKbps: Int): Long
    external fun encode(handle: Long, samples: ShortArray, samplesPerChannel: Int, output: ByteArray): Int
    external fun flush(handle: Long, output: ByteArray): Int
    external fun close(handle: Long)
}
