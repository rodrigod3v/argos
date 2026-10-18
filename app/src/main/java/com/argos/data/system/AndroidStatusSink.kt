package com.argos.data.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.argos.domain.port.StatusSink

/**
 * Reporta status: guarda o ultimo texto (pro console do app),
 * avisa observadores e atualiza uma notificacao discreta.
 */
class AndroidStatusSink(private val context: Context) : StatusSink {

    private val manager = context.getSystemService(NotificationManager::class.java)

    @Volatile
    var last: String = ""
        private set

    /** Observador unico (o console). Atribuido/limpo no ciclo de vida da Activity. */
    @Volatile
    var observer: ((String) -> Unit)? = null

    init {
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
            .apply { setShowBadge(false) }
        manager.createNotificationChannel(channel)
    }

    override fun report(text: String) {
        last = text
        observer?.invoke(text)
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(CHANNEL_NAME)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "argos"
        private const val CHANNEL_NAME = "Argos"
        private const val NOTIFICATION_ID = 1002
    }
}
