package com.argos.domain.port

/** Reporta o que o Argos esta fazendo (notificacao + console do app). */
interface StatusSink {
    fun report(text: String)
}
