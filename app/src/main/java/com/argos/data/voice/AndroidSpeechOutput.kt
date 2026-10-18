package com.argos.data.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import com.argos.domain.port.SpeechOutput
import java.util.Locale

/**
 * Voz de resposta. Engine criada sob demanda e recriavel —
 * release() + say() volta a subir, pra suportar o servico morrer e renascer.
 */
class AndroidSpeechOutput(private val context: Context) : SpeechOutput {

    private var engine: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    override fun say(text: String) {
        ensure()
        if (ready) engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        else pending = text
    }

    override fun release() {
        runCatching { engine?.shutdown() }
        engine = null
        ready = false
        pending = null
    }

    private fun ensure() {
        if (engine != null) return
        ready = false
        engine = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) engine?.language = Locale("pt", "BR")
            val queued = pending
            pending = null
            if (ready && queued != null) say(queued)
        }
    }

    private companion object {
        const val UTTERANCE_ID = "argos"
    }
}
