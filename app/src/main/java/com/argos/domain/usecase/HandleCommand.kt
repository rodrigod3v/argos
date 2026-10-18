package com.argos.domain.usecase

import com.argos.core.model.Command
import com.argos.domain.port.AppRepository
import com.argos.domain.port.ScreenController

/**
 * Command -> resposta curta pro usuario.
 * Unico lugar que executa acao. Ponto de entrada da politica de
 * confirmacao em operacoes irreversiveis (Tier 2), sem espalhar if pelo codigo.
 */
class HandleCommand(
    private val apps: AppRepository,
    private val screen: () -> ScreenController,
) {

    fun invoke(command: Command): String {
        val controller = screen()
        return when (command) {
            is Command.OpenApp -> openApp(command.query)

            is Command.Tap ->
                if (controller.tap(command.query)) "Toquei em \"${command.query}\"."
                else missing(controller) { "Nao achei \"${command.query}\" na tela." }

            is Command.TypeText ->
                if (controller.type(command.text)) "Escrevi."
                else missing(controller) { "Nenhum campo de texto na tela." }

            Command.Back -> global("Voltando", controller, controller.back())
            Command.Home -> global("Inicio", controller, controller.home())
            Command.Notifications -> global("Notificacoes", controller, controller.notifications())

            is Command.Unknown -> "Nao entendi \"${command.raw}\"."
        }
    }

    private fun openApp(query: String): String {
        val entry = apps.find(query) ?: return "Nao achei o app \"$query\"."
        return if (apps.launch(entry)) "Abrindo ${entry.label}."
        else "Nao consegui abrir ${entry.label}."
    }

    private fun missing(controller: ScreenController, fallback: () -> String): String =
        if (!controller.ready) ACCESSIBILITY_OFF else fallback()

    private fun global(label: String, controller: ScreenController, done: Boolean): String = when {
        !controller.ready -> ACCESSIBILITY_OFF
        done -> "$label."
        else -> "Acao indisponivel."
    }

    private companion object {
        const val ACCESSIBILITY_OFF = "Acessibilidade desligada. Liga no app."
    }
}
