package com.argos.data.screen

import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.argos.core.text.matchesWord
import com.argos.core.text.norm
import com.argos.domain.port.ScreenController

/**
 * Converte node tree em acao. Sem ciclo de vida, sem Android component.
 * Toda a logica de arvore vive aqui.
 */
class AccessibilityScreenController(private val nodes: NodeSource) : ScreenController {

    override val ready: Boolean get() = nodes.root() != null

    /** Match normalizado, depois sobe pro ancestral clicavel mais proximo. */
    override fun tap(query: String): Boolean {
        val q = query.norm()
        if (q.isEmpty()) return false

        val nodes = flatten()
        val match = nodes.firstOrNull { node ->
            val value = textOf(node).norm()
            value.isNotEmpty() && (value == q || value.contains(q))
        } ?: nodes.firstOrNull { node ->
            val value = textOf(node).norm()
            value.isNotEmpty() && value.matchesWord(q)
        } ?: return false

        val clickable = generateSequence(match) { it.parent }
            .firstOrNull { it.isClickable }
            ?: return false

        return clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    override fun type(text: String): Boolean {
        val editable = flatten().firstOrNull { it.isEditable && it.isFocused }
            ?: flatten().firstOrNull { it.isEditable }
            ?: return false

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    override fun back(): Boolean = nodes.back()
    override fun home(): Boolean = nodes.home()
    override fun notifications(): Boolean = nodes.notifications()

    override fun snapshot(): String {
        val out = StringBuilder(1024)
        flatten().forEachIndexed { index, node ->
            val text = textOf(node)
            val inert = text.isBlank() && !node.isClickable && !node.isEditable
            if (inert) return@forEachIndexed

            val bounds = Rect().also { node.getBoundsInScreen(it) }
            val kind = node.className?.toString()?.substringAfterLast('.') ?: "View"
            out.append(index).append(' ').append(kind)
                .append(" [").append(bounds.left).append(',').append(bounds.top)
                .append('-').append(bounds.right).append(',').append(bounds.bottom).append(']')
            if (node.isClickable) out.append(" clickable")
            if (node.isEditable) out.append(" editable")
            if (text.isNotBlank()) out.append(" \"").append(text.take(80)).append('"')
            out.append('\n')
        }
        return out.toString()
    }

    private fun flatten(): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>(64)
        collect(nodes.root(), out)
        return out
    }

    private fun collect(node: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        out.add(node)
        for (i in 0 until node.childCount) collect(node.getChild(i), out)
    }

    private fun textOf(node: AccessibilityNodeInfo): String {
        val text = node.text?.toString().orEmpty()
        return if (text.isNotBlank()) text else node.contentDescription?.toString().orEmpty()
    }
}
