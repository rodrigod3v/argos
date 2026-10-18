package com.argos.core.model

/**
 * Comando ja interpretado. Puro — nenhuma dependencia de Android.
 * A query carrega texto normalizado (norm()).
 */
sealed interface Command {
    data class OpenApp(val query: String) : Command
    data class Tap(val query: String) : Command
    data class TypeText(val text: String) : Command
    data object Back : Command
    data object Home : Command
    data object Notifications : Command
    data class Unknown(val raw: String) : Command
}
