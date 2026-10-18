package com.argos.data.voice

import java.io.File
import kotlin.math.roundToInt

/**
 * Leitor WAV PCM16 minimo, mono 16 kHz.
 * Usado pelo auto-teste: TTS escreve WAV -> Vosk transcreve o arquivo.
 */
object WavPcm16 {

    fun toMono16k(file: File): ShortArray {
        val bytes = file.readBytes()
        if (bytes.size < 44) return ShortArray(0)

        var fmtOffset = -1
        var dataOffset = -1
        var dataLength = 0

        var cursor = 12
        while (cursor + 8 <= bytes.size) {
            val id = String(bytes, cursor, 4, Charsets.US_ASCII)
            val size = le32(bytes, cursor + 4)
            when (id) {
                "fmt " -> fmtOffset = cursor + 8
                "data" -> {
                    dataOffset = cursor + 8
                    dataLength = size
                }
            }
            cursor += 8 + size + (size and 1)
        }
        if (fmtOffset < 0 || dataOffset < 0) return ShortArray(0)

        val channels = le16(bytes, fmtOffset + 2).coerceAtLeast(1)
        val sampleRate = le32(bytes, fmtOffset + 4)
        val bits = le16(bytes, fmtOffset + 14)
        if (bits != 16) return ShortArray(0)

        val available = minOf(dataLength, bytes.size - dataOffset)
        val frames = available / (2 * channels)
        val mono = ShortArray(frames)
        for (frame in 0 until frames) {
            // mistura simples: usa o primeiro canal
            mono[frame] = le16(bytes, dataOffset + frame * channels * 2).toShort()
        }

        return if (sampleRate == TARGET_RATE) mono else resample(mono, sampleRate, TARGET_RATE)
    }

    private fun resample(input: ShortArray, from: Int, to: Int): ShortArray {
        if (input.isEmpty() || from <= 0 || from == to) return input
        val size = (input.size.toLong() * to / from).toInt().coerceAtLeast(1)
        val out = ShortArray(size)
        for (i in out.indices) {
            val position = i.toDouble() * from / to
            val left = position.toInt().coerceIn(0, input.size - 1)
            val right = (left + 1).coerceAtMost(input.size - 1)
            val fraction = position - left
            out[i] = ((1 - fraction) * input[left] + fraction * input[right]).roundToInt().toShort()
        }
        return out
    }

    private fun le16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun le32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private const val TARGET_RATE = 16000
}
