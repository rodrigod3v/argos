package com.argos.domain.port

/**
 * As maos. Contrato puro: nenhum tipo de Android vaza pra ca.
 * Quando o servico de acessibilidade esta desligado, usa NullScreenController.
 */
interface ScreenController {
    /** true quando ha uma janela ativa legivel. */
    val ready: Boolean

    fun tap(query: String): Boolean
    fun type(text: String): Boolean
    fun back(): Boolean
    fun home(): Boolean
    fun notifications(): Boolean

    /** Serializa a tela em texto. Entrada do LLM no Tier 2. */
    fun snapshot(): String
}
