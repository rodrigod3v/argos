package com.argos.core.text

import java.text.Normalizer
import java.util.Locale

/**
 * Normaliza texto pra comparacao. minusculo + sem acento.
 * Usado nos DOIS lados de todo match (comando falado E label/texto de tela),
 * senao acento quebra o reconhecimento.
 */
fun String.norm(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .trim()

/** Remove pontuacao que o STT costuma grudar nas bordas ("abrir prumo." / "abrir, prumo"). */
fun String.stripEdgePunct(): String = trim(*EDGE_PUNCT)

private val COMBINING_MARKS = Regex("\\p{Mn}+")

private val EDGE_PUNCT = charArrayOf(
    ' ', '\t', '\n', '.', ',', '!', '?', ';', ':', '"', '\'', '-', '…',
)
