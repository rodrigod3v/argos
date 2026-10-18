package com.argos.entry

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.argos.core.text.norm
import com.argos.data.voice.VoskModelInstaller
import com.argos.data.voice.WavPcm16
import com.argos.di.Graph
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.util.Locale

/**
 * Auto-teste (debug): TTS sintetiza a frase -> WAV -> Vosk transcreve.
 *
 * Prova o modelo e a precisao sem depender de alguem falar no microfone.
 * Nao roda em release (chamado so sob BuildConfig.DEBUG).
 */
class DebugSelfTest(private val context: Context) {

    private var tts: TextToSpeech? = null

    fun run(phrase: String, onResult: (String) -> Unit) {
        val wav = File(context.cacheDir, "selftest.wav").apply { delete() }

        tts = TextToSpeech(context) { status ->
            val engine = tts
            if (status != TextToSpeech.SUCCESS || engine == null) {
                Log.e(TAG, "selftest: TTS indisponivel (status=$status)")
                engine?.shutdown()
                onResult("TTS indisponivel")
                return@TextToSpeech
            }
            engine.language = Locale("pt", "BR")
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "selftest: TTS falhou")
                    engine.shutdown()
                    onResult("TTS falhou")
                }

                override fun onDone(utteranceId: String?) {
                    engine.shutdown()
                    transcribe(wav, onResult)
                }
            })
            engine.synthesizeToFile(phrase, null, wav, UTTERANCE)
        }
    }

    private fun transcribe(wav: File, onResult: (String) -> Unit) {
        Thread({
            val outcome = runCatching {
                check(wav.exists()) { "WAV nao gerado" }
                // o endpointer do Vosk precisa de silencio de cauda pra fechar a
                // ultima palavra. No mic real ele existe; no WAV nao. Recria.
                val speech = WavPcm16.toMono16k(wav)
                check(speech.isNotEmpty()) { "WAV invalido (${wav.length()} bytes)" }
                val pcm = speech + ShortArray((SAMPLE_RATE * TAIL_SILENCE_SECONDS).toInt())

                val dir = VoskModelInstaller(context).ensure()
                val model = Model(dir.absolutePath)
                try {
                    val free = runRecognizer(model, pcm, null)
                    val gram = runRecognizer(model, pcm, grammarFor())
                    "livre=$free | gramatica=$gram"
                } finally {
                    model.close()
                }
            }.getOrElse { "ERRO: ${it.message}" }

            Log.i(TAG, "selftest -> $outcome")
            Handler(Looper.getMainLooper()).post { onResult(outcome) }
        }, "argos-selftest").start()
    }

    private fun runRecognizer(model: Model, pcm: ShortArray, grammar: String?): String {
        val recognizer = if (grammar == null) Recognizer(model, SAMPLE_RATE)
        else Recognizer(model, SAMPLE_RATE, grammar)
        try {
            var offset = 0
            while (offset < pcm.size) {
                val count = minOf(CHUNK, pcm.size - offset)
                recognizer.acceptWaveForm(pcm.copyOfRange(offset, offset + count), count)
                offset += count
            }
            return recognizer.finalResult
        } finally {
            recognizer.close()
        }
    }

    /**
     * Gramatica restrita com os nomes dos apps instalados.
     * "chrome" e "prumo" nao existem no lexico do modelo, entao nunca sao
     * reconhecidos em vocabulario livre. Na gramatica, viram vocabulario.
     */
    private fun grammarFor(): String {
        val phrases = mutableListOf("[unk]")
        val names = Graph.apps.all()
            .map { it.label.norm() }
            .filter { it.isNotBlank() }
        for (name in names) {
            phrases += "abrir $name"
            phrases += "abre $name"
            phrases += "abrir o $name"
            phrases += "abre o $name"
        }
        return phrases.joinToString(prefix = "[", postfix = "]", separator = ",") {
            org.json.JSONObject.quote(it)
        }
    }

    private companion object {
        const val TAG = "Argos"
        const val UTTERANCE = "argos-selftest"
        const val SAMPLE_RATE = 16000.0f
        const val CHUNK = 4000
        const val TAIL_SILENCE_SECONDS = 1.0f
    }
}
