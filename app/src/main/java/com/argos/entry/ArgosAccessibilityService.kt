package com.argos.entry

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.argos.data.screen.NodeSource
import com.argos.di.Graph

/**
 * Componente Android fino: so registra/desregistra o ScreenController.
 * Toda a logica de arvore vive em AccessibilityScreenController.
 *
 * O usuario precisa ligar isto manualmente uma vez em
 * Configuracoes > Acessibilidade. O Android nao permite auto-habilitar.
 */
class ArgosAccessibilityService : AccessibilityService(), NodeSource {

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
        Graph.installScreen(this)
    }

    override fun onDestroy() {
        connected = false
        Graph.clearScreen()
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    // Seam do Tier 2: debounce de typeWindowContentChanged -> snapshot -> LLM.

    // ---- NodeSource --------------------------------------------------------

    override fun root(): AccessibilityNodeInfo? = rootInActiveWindow
    override fun back(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    override fun home(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    override fun notifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    companion object {
        /** true enquanto o servico estiver habilitado pelo usuario. */
        @Volatile
        var connected: Boolean = false
            private set
    }
}
