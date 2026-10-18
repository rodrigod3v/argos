package com.argos.data.screen

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Ponte com o AccessibilityService. Implementado pela camada entry.
 * Existe pra que AccessibilityScreenController nao dependa do componente Android
 * (fica testavel com um NodeSource falso).
 */
interface NodeSource {
    fun root(): AccessibilityNodeInfo?
    fun back(): Boolean
    fun home(): Boolean
    fun notifications(): Boolean
}
