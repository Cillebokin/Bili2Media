package com.example.bili2media.export.mp3.engine

import java.io.OutputStream

internal class LameMp3Encoder(
    sampleRate: Int,
    internal val channels: Int,
    bitrateKbps: Int
) : AutoCloseable {
    private var handle = NativeLame.create(sampleRate, channels, bitrateKbps)

    init {
        check(handle != 0L) { "LAME could not initialize the encoder" }
    }

    fun encode(samples: ShortArray, output: OutputStream) {
        require(samples.size % channels == 0)
        val samplesPerChannel = samples.size / channels
        if (samplesPerChannel == 0) return
        val capacity = (samplesPerChannel * 5L / 4L + ENCODER_BUFFER_PADDING)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        val encoded = ByteArray(capacity)
        val bytes = NativeLame.encode(handle, samples, samplesPerChannel, encoded)
        check(bytes >= 0) { "LAME failed to encode PCM samples" }
        if (bytes > 0) output.write(encoded, 0, bytes)
    }

    fun finish(output: OutputStream) {
        val encoded = ByteArray(FLUSH_BUFFER_SIZE)
        val bytes = NativeLame.flush(handle, encoded)
        check(bytes >= 0) { "LAME failed to flush the MP3 stream" }
        if (bytes > 0) output.write(encoded, 0, bytes)
    }

    override fun close() {
        val current = handle
        if (current != 0L) {
            handle = 0L
            NativeLame.close(current)
        }
    }

    private companion object {
        const val ENCODER_BUFFER_PADDING = 7_200L
        const val FLUSH_BUFFER_SIZE = 16_384 + 128 * 1_024
    }
}
