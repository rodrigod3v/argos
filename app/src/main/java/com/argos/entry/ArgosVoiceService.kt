package com.argos.entry

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.argos.core.grammar.AppGrammar
import com.argos.core.model.Command
import com.argos.core.wake.WakePhrase
import com.argos.data.system.AndroidStatusSink
import com.argos.di.Graph
import com.argos.domain.port.SpeechInput
import com.argos.domain.port.SpeechResult

/**
 * Loop de voz + keep-alive. Foreground service tipo microphone,
 * senao o Android mata o microfone em background.
 *
 * Ouvidos sao continuos (Vosk). O gate de wake word fica no texto.
 */
class ArgosVoiceService : Service(), SpeechInput.Listener {

    private val input get() = Graph.speechInput
    private val output get() = Graph.speechOutput
    private val status get() = Graph.status
    private val handle get() = Graph.handleCommand
    private val router get() = Graph.router

    private val handler = Handler(Looper.getMainLooper())

    /** falhas consecutivas de escuta — alimenta o backoff. */
    private var failureStreak = 0

    override fun onCreate() {
        super.onCreate()
        input.setListener(this)
        startForeground(NOTIFICATION_ID, buildNotification(IDLE_HINT))
        running = true
        status.report("Preparando escuta offline...")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        input.start()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        input.release()
        output.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---- pipeline ----------------------------------------------------------

    override fun onSpeechResult(result: SpeechResult) {
        when (result) {
            is SpeechResult.Heard -> {
                failureStreak = 0
                if (!transcribe(result.text)) scheduleRelisten()
            }
            // silencio/timeout/erro: so reescuta, com backoff
            is SpeechResult.Failed -> {
                failureStreak++
                scheduleRelisten()
            }
        }
    }

    /**
     * @return true se um comando foi engajado.
     *
     * Reporta SEMPRE o que ouviu. Antes so havia reacao depois da wake word:
     * quando o STT errava "argos" o app ficava mudo e parecia morto.
     *
     * Aceita tambem comando sem a wake word — se a frase ja e um comando valido,
     * executa. Num assistente pessoal isso vale mais que a pureza do gate.
     */
    private fun transcribe(raw: String): Boolean {
        status.report("Ouvi: \"$raw\"")
        val afterWake = WakePhrase.extract(raw)
        val text = afterWake ?: raw
        val command = router.route(text)

        if (command is Command.Unknown) {
            // so responde "nao entendi" quando foi de fato chamado
            if (afterWake != null) respond(command)
            return afterWake != null
        }

        if (command is Command.OpenApp && Graph.apps.find(command.query) == null) {
            rescue(command)
        } else {
            respond(command)
        }
        return true
    }

    /**
     * O vocabulario livre erra nome proprio ("chrome" vira "mim", "prumo" vira
     * "para um"). Aqui o MESMO audio e re-decodificado com a gramatica dos apps
     * instalados, que transforma esses nomes em vocabulario.
     *
     * So roda quando o app nao foi achado — no caminho comum custa zero.
     * Fora da main: decodificar leva centenas de ms.
     */
    private fun rescue(original: Command.OpenApp) {
        Thread({
            val retry = runCatching { input.redecode(AppGrammar.build(Graph.apps.all())) }.getOrNull()
            val resolved = retry?.let { hypothesis ->
                val text = WakePhrase.extract(hypothesis) ?: hypothesis
                (router.route(text) as? Command.OpenApp)
                    ?.takeIf { Graph.apps.find(it.query) != null }
            }
            if (resolved != null) Log.i(TAG, "redecode recuperou: \"$retry\"")
            handler.post { respond(resolved ?: original) }
        }, "argos-rescue").start()
    }

    private fun respond(command: Command) {
        val reply = handle.invoke(command)
        status.report(reply)
        // pausar a escuta enquanto fala, senao o Vosk ouve a propria voz
        input.stop()
        output.say(reply)
        handler.postDelayed({ if (running) input.start() }, AFTER_SPEECH_MS)
    }

    /**
     * Backoff: falha seguida nao pode martelar o microfone.
     * 400ms -> 800 -> 1600 -> ... -> teto de 5s. Zera quando ouve fala.
     */
    private fun scheduleRelisten() {
        val delay = (BASE_RELISTEN_MS shl failureStreak.coerceAtMost(MAX_BACKOFF_SHIFT))
            .coerceAtMost(MAX_RELISTEN_MS)
        handler.postDelayed({ if (running) input.start() }, delay)
    }

    // ---- notificacao -------------------------------------------------------

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, AndroidStatusSink.CHANNEL_ID)
            .setContentTitle("Argos ativo")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        const val TAG = "Argos"
        const val ACTION_START = "com.argos.action.START"
        const val ACTION_STOP = "com.argos.action.STOP"
        const val IDLE_HINT = "Diga \"Argos\" + comando"

        private const val NOTIFICATION_ID = 1001
        private const val BASE_RELISTEN_MS = 400L
        private const val MAX_RELISTEN_MS = 5000L
        private const val MAX_BACKOFF_SHIFT = 4

        /** tempo pra TTS terminar antes de reabrir o microfone */
        private const val AFTER_SPEECH_MS = 1800L

        @Volatile
        var running: Boolean = false
            private set
    }
}
