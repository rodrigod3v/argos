package com.argos.core.wake

import com.argos.core.text.closeTo
import com.argos.core.text.norm
import com.argos.core.text.stripEdgePunct

/**
 * Gate de wake word por texto. Puro e testavel.
 * O upgrade pra KWS acustico (Porcupine) troca so a camada de entrada —
 * esta funcao continua sendo o contrato.
 */
object WakePhrase {

    const val WORD = "argos"

    /**
     * Extrai o comando que vem depois da wake word.
     *
     * Compara palavra a palavra e tolera 1 caractere de erro: o STT erra
     * "argos" para "argus", "argo", "algos". Exigir igualdade exata era o
     * motivo de o assistente parecer surdo.
     *
     * @return null quando a wake word nao aparece OU nao sobra comando.
     */
    fun extract(transcript: String): String? {
        val tokens = transcript.stripEdgePunct().norm()
            .split(' ')
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null

        val at = tokens.indexOfFirst { it == WORD || it.closeTo(WORD, 1) }
        if (at < 0) return null

        return tokens.drop(at + 1).joinToString(" ").stripEdgePunct().ifEmpty { null }
    }
}
