package com.argos.core.wake

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
     * @return null quando a wake word nao aparece OU nao sobra comando.
     */
    fun extract(transcript: String): String? {
        val text = transcript.stripEdgePunct().norm()
        val at = text.indexOf(WORD)
        if (at < 0) return null
        return text.substring(at + WORD.length).stripEdgePunct().ifEmpty { null }
    }
}
