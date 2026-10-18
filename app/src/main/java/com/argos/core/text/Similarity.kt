package com.argos.core.text

/**
 * Distancia de edicao com corte antecipado.
 *
 * O STT erra nome proprio por pouco: "prumo" sai "pruma", "prudu", "brumo".
 * Um match exato perde; distancia 1-2 recupera.
 */
fun String.closeTo(other: String, max: Int): Boolean {
    if (this == other) return true
    if (max < 0) return false
    if (kotlin.math.abs(length - other.length) > max) return false

    var previous = IntArray(other.length + 1) { it }
    var current = IntArray(other.length + 1)

    for (i in 1..length) {
        current[0] = i
        var rowMin = current[0]
        for (j in 1..other.length) {
            val cost = if (this[i - 1] == other[j - 1]) 0 else 1
            current[j] = minOf(
                previous[j] + 1,
                current[j - 1] + 1,
                previous[j - 1] + cost,
            )
            if (current[j] < rowMin) rowMin = current[j]
        }
        if (rowMin > max) return false
        val swap = previous
        previous = current
        current = swap
    }
    return previous[other.length] <= max
}

/** tolerancia proporcional ao tamanho da palavra falada. */
fun toleranceFor(query: String): Int = when {
    query.length <= 3 -> 0
    query.length <= 5 -> 1
    else -> 2
}

/**
 * true se alguma palavra do texto casa com a query por distancia.
 * Compara palavra a palavra porque o comando fala um pedaco do rotulo
 * ("prumo" contra "Prumo Entregador").
 */
fun String.matchesWord(query: String, max: Int = toleranceFor(query)): Boolean =
    split(' ', '-', '_', '.', ',', '/', '(', ')', ':').any { word ->
        word.isNotEmpty() && (word == query || word.startsWith(query) || word.closeTo(query, max))
    }
