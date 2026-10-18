package com.argos.core.routing

import com.argos.core.model.Command
import com.argos.core.text.norm
import com.argos.core.text.stripEdgePunct

/**
 * Tier 0: intent deterministico. Sem LLM, sem rede, sem Android.
 *
 * O verbo vem do VerbLexicon (corpus pt-BR), nao de regex chumbada.
 * O resto do enunciado e o argumento.
 */
class CommandRouter(private val verbs: VerbLexicon) {

    fun route(raw: String): Command {
        val text = raw.stripEdgePunct().norm()
        if (text.isEmpty()) return Command.Unknown(raw)

        if (text in HOME_FORMS) return Command.Home
        if (text in BACK_FORMS) return Command.Back
        if (text in NOTIFICATION_FORMS) return Command.Notifications

        val tokens = text.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return Command.Unknown(raw)

        val kind = verbs.kindOf(tokens[0]) ?: return Command.Unknown(raw)
        val rest = tokens.drop(1)

        return when (kind) {
            VerbKind.ABRIR, VerbKind.INICIAR, VerbKind.LANCAR, VerbKind.EXECUTAR, VerbKind.ENTRAR -> {
                val target = rest.dropLeadNoise()
                if (target.isEmpty()) Command.Unknown(raw) else Command.OpenApp(target)
            }

            VerbKind.TOCAR, VerbKind.APERTAR, VerbKind.SELECIONAR, VerbKind.CLICAR -> {
                val target = rest.dropLeadNoise()
                if (target.isEmpty()) Command.Unknown(raw) else Command.Tap(target)
            }

            // aqui o argumento e literal: nao tirar artigo, senao "digita ola" vira "ola"="ola"
            VerbKind.DIGITAR, VerbKind.ESCREVER, VerbKind.PESQUISAR, VerbKind.BUSCAR -> {
                val value = rest.joinToString(" ").trim()
                if (value.isEmpty()) Command.Unknown(raw) else Command.TypeText(value)
            }

            VerbKind.VOLTAR -> Command.Back
        }
    }

    /** remove artigo/substantivo do comeco: "o aplicativo prumo" -> "prumo". */
    private fun List<String>.dropLeadNoise(): String {
        var index = 0
        while (index < size && index < MAX_LEAD_NOISE && this[index] in LEAD_NOISE) index++
        return drop(index).joinToString(" ").trim()
    }

    private companion object {
        const val MAX_LEAD_NOISE = 3

        val LEAD_NOISE = setOf("o", "a", "os", "as", "app", "aplicativo", "programa", "em", "no", "na")

        val HOME_FORMS = setOf("home", "tela inicial", "inicio", "pagina inicial")
        val BACK_FORMS = setOf("voltar", "volta", "back", "retroceder", "volta")
        val NOTIFICATION_FORMS = setOf("notificacoes", "barra de notificacoes", "painel de notificacoes")
    }
}
