package com.argos.data.screen

import com.argos.domain.port.ScreenController

/**
 * Estado padrao enquanto o servico de acessibilidade nao esta ligado.
 * Deixa ScreenController nunca-nulo, o que tira um if de todo call site.
 */
object NullScreenController : ScreenController {
    override val ready: Boolean get() = false
    override fun tap(query: String): Boolean = false
    override fun type(text: String): Boolean = false
    override fun back(): Boolean = false
    override fun home(): Boolean = false
    override fun notifications(): Boolean = false
    override fun snapshot(): String = ""
}
