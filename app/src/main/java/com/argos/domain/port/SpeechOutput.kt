package com.argos.domain.port

/** A voz de resposta. */
interface SpeechOutput {
    fun say(text: String)
    fun release()
}
