package com.argos.entry

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.argos.core.routing.CommandRouter
import com.argos.core.wake.WakePhrase
import com.argos.data.system.AndroidStatusSink
import com.argos.di.Graph
import com.argos.domain.port.SpeechInput
import com.argos.domain.port.SpeechResult

/**
 * Loop de voz + keep-alive. Foreground service tipo microphone,
 * senao o Android mata o microfone em background.
 *
 * Orquestra so isso: ouvidos -> wake -> router -> caso de uso -> fala.
 */
class ArgosVoiceService : Service(), SpeechInput.Listener {

    private val input get() = Graph.speechInput
    private val output get() = Graph.speechOutput
    private val status get() = Graph.status
    private val handle get() = Graph.handleCommand

    private val handler = Handler(Looper.getMainLooper())

    /** falhas consecutivas de escuta — alimenta o backoff. */
    private var failureStreak = 0

    override fun onCreate() {
        super.onCreate()
        input.setListener(this)
        startForeground(NOTIFICATION_ID, buildNotification(IDLE_HINT))
        running = true
        status.report("Ouvindo.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        listen()
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
                transcribe(result.text)
            }
            // Failed (silencio/timeout/ruido) nao e erro fatal: so reescuta.
            is SpeechResult.Failed -> failureStreak++
        }
        scheduleRelisten()
    }

    private fun transcribe(transcript: String) {
        status.report("Ouvi: \"$transcript\"")
        val commandText = WakePhrase.extract(transcript) ?: return
        val reply = handle.invoke(CommandRouter.route(commandText))
        status.report(reply)
        output.say(reply)
    }

    private fun listen() {
        if (!input.isListening) input.start()
    }

    /**
     * Backoff: falha seguida nao pode martelar o microfone.
     * 400ms -> 800 -> 1600 -> ... -> teto de 5s. Zera quando ouve algo.
     */
    private fun scheduleRelisten() {
        val delay = (BASE_RELISTEN_MS shl failureStreak.coerceAtMost(MAX_BACKOFF_SHIFT))
            .coerceAtMost(MAX_RELISTEN_MS)
        handler.postDelayed({ if (running && !input.isListening) input.start() }, delay)
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
        const val ACTION_START = "com.argos.action.START"
        const val ACTION_STOP = "com.argos.action.STOP"
        const val IDLE_HINT = "Diga \"Argos\" + comando"

        private const val NOTIFICATION_ID = 1001
        private const val BASE_RELISTEN_MS = 400L
        private const val MAX_RELISTEN_MS = 5000L

        /** 400ms << 4 = 6.4s, ja acima do teto — limita o deslocamento. */
        private const val MAX_BACKOFF_SHIFT = 4

        @Volatile
        var running: Boolean = false
            private set
    }
}
