package com.argos.core.routing

import com.argos.core.model.Command
import com.argos.core.text.norm
import com.argos.core.text.stripEdgePunct

/**
 * Tier 0: intent deterministico. Sem LLM, sem rede, sem Android.
 *
 * Entrada JA sem a wake word. Os padroes nao tem acento porque a string
 * de entrada passa por norm() antes de casar.
 */
object CommandRouter {

    // dois grupos opcionais em sequencia (artigo + substantivo) pra
    // "abrir o aplicativo prumo" capturar so "prumo".
    private val openApp = Regex(
        "^\\s*(?:abrir|abre|abri|lancar|lanca|executar|executa|iniciar|inicia|entrar no|entra no)\\s+" +
            "(?:o\\s+|a\\s+|os\\s+|as\\s+)?" +
            "(?:app\\s+|aplicativo\\s+|programa\\s+)?" +
            "(.+?)\\s*$",
    )

    private val tap = Regex(
        "^\\s*(?:toca|tocar|clica|clicar|aperta|apertar|seleciona|selecionar)\\s+" +
            "(?:em\\s+|no\\s+|na\\s+|o\\s+|a\\s+)?(.+?)\\s*$",
    )

    private val typeText = Regex(
        "^\\s*(?:digita|digitar|escreve|escrever|pesquisa|pesquisar|busca|buscar)\\s+(.+?)\\s*$",
    )

    private val home = Regex("^\\s*(?:home|tela inicial|inicio|pagina inicial)\\s*$")
    private val back = Regex("^\\s*(?:voltar|volta|back|retroceder)\\s*$")
    private val notifications =
        Regex("^\\s*(?:notificacoes|barra de notificacoes|painel de notificacoes)\\s*$")

    fun route(raw: String): Command {
        val text = raw.stripEdgePunct().norm()
        if (text.isEmpty()) return Command.Unknown(raw)

        home.matchEntire(text)?.let { return Command.Home }
        back.matchEntire(text)?.let { return Command.Back }
        notifications.matchEntire(text)?.let { return Command.Notifications }

        openApp.matchEntire(text)?.let { match ->
            val query = match.groupValues[1].stripEdgePunct().norm()
            if (query.isNotEmpty()) return Command.OpenApp(query)
        }
        tap.matchEntire(text)?.let { match ->
            val query = match.groupValues[1].stripEdgePunct().norm()
            if (query.isNotEmpty()) return Command.Tap(query)
        }
        typeText.matchEntire(text)?.let { match ->
            val value = match.groupValues[1].stripEdgePunct()
            if (value.isNotEmpty()) return Command.TypeText(value)
        }

        return Command.Unknown(raw)
    }
}
