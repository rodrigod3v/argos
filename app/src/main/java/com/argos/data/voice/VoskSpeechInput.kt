package com.argos.data.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.argos.domain.port.SpeechInput
import com.argos.domain.port.SpeechResult
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.util.concurrent.Executors

/**
 * Ouvidos 100% offline via Vosk. Zero rede.
 *
 * Controla o AudioRecord direto (nao usa o SpeechService do Vosk) por um motivo:
 * precisa manter um buffer circular do audio recente. Quando a transcricao livre
 * erra um nome proprio, o mesmo audio e re-decodificado com uma gramatica
 * restrita — e isso recupera "chrome" e "prumo".
 */
class VoskSpeechInput(
    private val installer: VoskModelInstaller,
) : SpeechInput {

    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "argos-vosk").apply { isDaemon = true }
    }

    @Volatile private var listener: SpeechInput.Listener? = null

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var recorder: AudioRecord? = null
    private var audioThread: Thread? = null

    /** ultimos segundos de audio, para re-decode */
    private val ring = ShortRing(SAMPLE_RATE * RING_SECONDS)

    /** audio exato do ultimo enunciado — e o que o re-decode usa */
    @Volatile private var lastUtterance: ShortArray = ShortArray(0)

    @Volatile
    override var isListening: Boolean = false
        private set

    override val supportsOffline: Boolean get() = true

    override fun setListener(listener: SpeechInput.Listener) {
        this.listener = listener
    }

    override fun start() {
        if (isListening) return
        worker.execute {
            runCatching { startBlocking() }.onFailure { fail("iniciar", it) }
        }
    }

    override fun stop() {
        if (!isListening) return
        isListening = false
        worker.execute { runCatching { teardownAudio() } }
    }

    override fun release() {
        isListening = false
        worker.execute {
            runCatching { teardownAudio() }
            runCatching { recognizer?.close() }
            runCatching { model?.close() }
            recognizer = null
            model = null
            Log.i(TAG, "vosk liberado")
        }
    }

    /**
     * Re-decodifica o audio do buffer com a gramatica dada.
     * Chamado so no caminho de erro, entao o custo e raro.
     */
    override fun redecode(grammar: String): String? {
        if (grammar.isBlank()) return null
        val loadedModel = model ?: return null
        val pcm = lastUtterance
        if (pcm.size < SAMPLE_RATE / 2) return null

        return runCatching {
            val rescoring = Recognizer(loadedModel, SAMPLE_RATE_F, grammar)
            try {
                var offset = 0
                while (offset < pcm.size) {
                    val count = minOf(CHUNK, pcm.size - offset)
                    rescoring.acceptWaveForm(pcm.copyOfRange(offset, offset + count), count)
                    offset += count
                }
                textOf(rescoring.finalResult)
            } finally {
                rescoring.close()
            }
        }.onFailure { Log.w(TAG, "redecode falhou", it) }.getOrNull()
    }

    // ---- ciclo de vida do audio -------------------------------------------

    private fun startBlocking() {
        if (isListening) return

        val dir = installer.ensure()
        val loadedModel = model ?: Model(dir.absolutePath).also {
            model = it
            Log.i(TAG, "modelo carregado de ${dir.name}")
        }
        val loadedRecognizer = recognizer ?: Recognizer(loadedModel, SAMPLE_RATE_F).also {
            recognizer = it
        }

        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        check(minBuffer > 0) { "AudioRecord indisponivel (buffer $minBuffer)" }

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            minBuffer * 2,
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) {
            "microfone indisponivel (estado ${record.state})"
        }

        recorder = record
        isListening = true
        ring.clear()
        record.startRecording()

        audioThread = Thread({ audioLoop(record, loadedRecognizer) }, "argos-audio").apply {
            isDaemon = true
            start()
        }
        Log.i(TAG, "escutando offline (${SAMPLE_RATE} Hz)")
    }

    private fun audioLoop(record: AudioRecord, recognizer: Recognizer) {
        val buffer = ShortArray(CHUNK)
        try {
            while (isListening) {
                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                ring.write(buffer, read)

                if (!recognizer.acceptWaveForm(buffer, read)) continue
                // guarda o audio do enunciado ANTES de limpar: o re-decode depende dele
                lastUtterance = ring.snapshot()
                ring.clear()
                val text = textOf(recognizer.result)
                if (text.isNotEmpty()) {
                    listener?.onSpeechResult(SpeechResult.Heard(text))
                }
            }
        } catch (throwable: Throwable) {
            if (isListening) fail("ler audio", throwable)
        }
    }

    private fun teardownAudio() {
        runCatching { recorder?.stop() }
        audioThread?.join(500)
        audioThread = null
        runCatching { recorder?.release() }
        recorder = null
    }

    private fun fail(where: String, error: Throwable) {
        isListening = false
        Log.e(TAG, "falha ao $where", error)
        listener?.onSpeechResult(SpeechResult.Failed(CODE_FAIL))
    }

    private fun textOf(hypothesis: String?): String {
        if (hypothesis.isNullOrBlank()) return ""
        return runCatching { JSONObject(hypothesis).optString("text", "") }
            .getOrDefault("")
            .trim()
    }

    private companion object {
        const val TAG = "Argos"
        const val SAMPLE_RATE = 16000
        const val SAMPLE_RATE_F = 16000.0f
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        /** 20 buffers de 200 ms por segundo */
        const val CHUNK = 3200
        const val RING_SECONDS = 8
        const val CODE_FAIL = -100
    }
}
