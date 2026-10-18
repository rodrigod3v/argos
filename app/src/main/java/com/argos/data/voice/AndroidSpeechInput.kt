package com.argos.data.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.argos.domain.port.SpeechInput
import com.argos.domain.port.SpeechResult

/**
 * Ouvidos via SpeechRecognizer.
 *
 * API 31+ usa reconhecimento on-device. Em aparelho mais antigo cai no
 * SpeechRecognizer normal, que roda no processo do app do Google —
 * por isso NAO precisamos da permissao INTERNET aqui.
 */
class AndroidSpeechInput(private val context: Context) : SpeechInput {

    private var recognizer: SpeechRecognizer? = null
    private var listener: SpeechInput.Listener? = null

    override var isListening: Boolean = false
        private set

    override val supportsOffline: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    override fun setListener(listener: SpeechInput.Listener) {
        this.listener = listener
    }

    override fun start() {
        if (isListening) return
        isListening = true
        runCatching { recognizer().startListening(intent()) }
            .onFailure {
                isListening = false
                listener?.onSpeechResult(SpeechResult.Failed(SpeechRecognizer.ERROR_CLIENT))
            }
    }

    override fun stop() {
        if (!isListening) return
        isListening = false
        runCatching { recognizer?.stopListening() }
    }

    override fun release() {
        isListening = false
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun recognizer(): SpeechRecognizer {
        recognizer?.let { return it }
        val created = if (supportsOffline) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        created.setRecognitionListener(callback)
        recognizer = created
        return created
    }

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, LOCALE)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, LOCALE)
        // So pede offline quando o aparelho realmente tem modelo local.
        // Em API < 31 isso faz o servico do sistema recusar na hora.
        if (supportsOffline) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
    }

    private val callback = object : RecognitionListener {
        override fun onResults(results: Bundle) {
            isListening = false
            val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull { !it.isNullOrBlank() }
            if (text.isNullOrBlank()) {
                Log.w(TAG, "sem resultado")
                listener?.onSpeechResult(SpeechResult.Failed(SpeechRecognizer.ERROR_NO_MATCH))
            } else {
                listener?.onSpeechResult(SpeechResult.Heard(text))
            }
        }

        override fun onError(error: Int) {
            isListening = false
            Log.w(TAG, "erro do reconhecedor code=$error (${errorName(error)})")
            listener?.onSpeechResult(SpeechResult.Failed(error))
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            isListening = false
        }

        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private companion object {
        const val LOCALE = "pt-BR"
        const val TAG = "Argos"

        fun errorName(code: Int): String = when (code) {
            SpeechRecognizer.ERROR_AUDIO -> "AUDIO"
            SpeechRecognizer.ERROR_CLIENT -> "CLIENT"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "PERMISSOES"
            SpeechRecognizer.ERROR_NETWORK -> "REDE"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "REDE_TIMEOUT"
            SpeechRecognizer.ERROR_NO_MATCH -> "SEM_MATCH"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "OCUPADO"
            SpeechRecognizer.ERROR_SERVER -> "SERVIDOR"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "SILENCIO"
            else -> "?"
        }
    }
}
