package com.argos.data.voice

/**
 * Buffer circular de amostras PCM. Guarda os ultimos N segundos para re-decode.
 * Escrita na thread de audio, leitura na thread de trabalho — sincronizado.
 */
class ShortRing(private val capacity: Int) {

    private val data = ShortArray(capacity)
    private var cursor = 0
    private var filled = 0

    @Synchronized
    fun write(source: ShortArray, length: Int) {
        val count = minOf(length, source.size)
        for (i in 0 until count) {
            data[cursor] = source[i]
            cursor = (cursor + 1) % capacity
        }
        filled = minOf(capacity, filled + count)
    }

    /** Copia em ordem cronologica. */
    @Synchronized
    fun snapshot(): ShortArray {
        val out = ShortArray(filled)
        val start = ((cursor - filled) % capacity + capacity) % capacity
        for (i in 0 until filled) out[i] = data[(start + i) % capacity]
        return out
    }

    @Synchronized
    fun clear() {
        cursor = 0
        filled = 0
    }
}
