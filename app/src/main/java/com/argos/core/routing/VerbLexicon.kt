package com.argos.core.routing

/** Familia de verbo reconhecida. Define o que o comando FAZ. */
enum class VerbKind {
    ABRIR, INICIAR, LANCAR, EXECUTAR, ENTRAR,
    TOCAR, APERTAR, SELECIONAR, CLICAR,
    DIGITAR, ESCREVER, PESQUISAR, BUSCAR,
    VOLTAR,
}

/**
 * Dicionario de formas verbais: forma conjugada -> familia.
 *
 * Vem do corpus github.com/fserb/pt-br (conjugacoes + listas/verbos), reduzido
 * as familias que interessam. Isso cobre "abre/abriu/abria/abram/abrindo" sem
 * lista chumbada na mao — e o corpus ja provou que anglicismo nao existe nele
 * ("clicar" tinha zero formas), por isso ha um piso embutido.
 *
 * Puro: sem Android. Carregado de assets pela camada data.
 */
class VerbLexicon private constructor(private val byForm: Map<String, VerbKind>) {

    fun kindOf(form: String): VerbKind? = byForm[form]

    val size: Int get() = byForm.size

    companion object {

        /** essencial, sempre presente, mesmo sem o asset. */
        private val FLOOR: Map<String, VerbKind> = buildMap {
            for (form in listOf("abre", "abrir", "abri", "abre", "abriu", "abra")) put(form, VerbKind.ABRIR)
            for (form in listOf("toca", "tocar", "tocou")) put(form, VerbKind.TOCAR)
            for (form in listOf("clica", "clicar", "clique")) put(form, VerbKind.CLICAR)
            for (form in listOf("aperta", "apertar")) put(form, VerbKind.APERTAR)
            for (form in listOf("seleciona", "selecionar")) put(form, VerbKind.SELECIONAR)
            for (form in listOf("digita", "digitar")) put(form, VerbKind.DIGITAR)
            for (form in listOf("escreve", "escrever")) put(form, VerbKind.ESCREVER)
            for (form in listOf("pesquisa", "pesquisar")) put(form, VerbKind.PESQUISAR)
            for (form in listOf("busca", "buscar")) put(form, VerbKind.BUSCAR)
            for (form in listOf("volta", "voltar")) put(form, VerbKind.VOLTAR)
            for (form in listOf("inicia", "iniciar")) put(form, VerbKind.INICIAR)
            for (form in listOf("lanca", "lancar")) put(form, VerbKind.LANCAR)
            for (form in listOf("executa", "executar")) put(form, VerbKind.EXECUTAR)
            for (form in listOf("entra", "entrar")) put(form, VerbKind.ENTRAR)
        }

        val EMPTY: VerbLexicon = VerbLexicon(FLOOR)

        /**
         * @param lines formato "INTENT forma". Linhas com # sao ignoradas.
         */
        fun from(lines: Sequence<String>): VerbLexicon {
            val map = HashMap<String, VerbKind>(FLOOR)
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                val separator = trimmed.indexOf(' ')
                if (separator <= 0) continue
                val kind = runCatching {
                    VerbKind.valueOf(trimmed.substring(0, separator).uppercase())
                }.getOrNull() ?: continue
                val form = trimmed.substring(separator + 1).trim()
                if (form.isNotEmpty()) map[form] = kind
            }
            return VerbLexicon(map)
        }
    }
}
