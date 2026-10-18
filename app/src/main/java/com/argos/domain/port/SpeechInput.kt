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

    fun interface Listener {
        fun onSpeechResult(result: SpeechResult)
    }
}
