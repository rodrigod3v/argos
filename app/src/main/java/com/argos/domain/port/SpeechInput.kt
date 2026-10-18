package com.argos.domain.port

/** Resultado de uma rodada de escuta. */
sealed interface SpeechResult {
    data class Heard(val text: String) : SpeechResult
    data class Failed(val code: Int) : SpeechResult
}

/**
 * Os ouvidos. O dono SEMPRE reinicia apos onSpeechResult -> loop continuo.
 */
interface SpeechInput {

    val isListening: Boolean
    val supportsOffline: Boolean

    fun setListener(listener: Listener)
    fun start()
    fun stop()
    fun release()

    /**
     * Re-decodifica o audio recente com uma gramatica restrita.
     *
     * Existe para um caso especifico: o vocabulario livre erra um nome proprio
     * ("chrome" vira "mim"). Re-decodificar o MESMO audio com os nomes dos apps
     * instalados como gramatica acerta. So e chamado quando o app nao foi achado,
     * entao nao custa nada no caminho comum.
     *
     * @return transcricao, ou null quando a implementacao nao suporta.
     */
    fun redecode(grammar: String): String? = null

    fun interface Listener {
        fun onSpeechResult(result: SpeechResult)
    }
}
