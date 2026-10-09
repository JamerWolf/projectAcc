package com.example.projectacc.a11y

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.projectacc.SERVICE_TAG

/**
 * Pure helpers to read from and act on the accessibility tree.
 * Every function receives the window or node it works on explicitly;
 * the object keeps no state.
 */
@Suppress("DEPRECATION")
object AccessibilityTree {

    /**
     * Aplana el árbol en una lista de Content Descriptions para facilitar la búsqueda por posición.
     */
    fun flattenContentDescriptions(
        node: AccessibilityNodeInfo?,
        list: MutableList<String>
    ) {
        if (node == null) return
        val cd = node.contentDescription?.toString() ?: ""
        if (cd.isNotEmpty()) list.add(cd)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            flattenContentDescriptions(child, list)
            child?.recycle()
        }
    }

    fun extractAllText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()
        extractTextRecursive(node, sb)
        return sb.toString()
    }

    private fun extractTextRecursive(node: AccessibilityNodeInfo, sb: StringBuilder) {
        val text = node.text?.toString() ?: ""
        if (text.isNotEmpty()) {
            sb.appendLine(text)
        }
        val cd = node.contentDescription?.toString() ?: ""
        if (cd.isNotEmpty() && cd != text) {
            sb.appendLine(cd)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractTextRecursive(child, sb)
                child.recycle()
            }
        }
    }

    fun findEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val className = node.className?.toString() ?: ""
        if (className.contains("EditText") || className.contains("Input")) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditText(child)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    /**
     * Busca recursivamente el valor numérico del porcentaje (ej: "99%") en el árbol.
     */
    fun findPercentage(node: AccessibilityNodeInfo?): Int? {
        if (node == null) return null
        val contentDesc = node.contentDescription?.toString() ?: ""
        if (contentDesc.endsWith("%")) {
            val numericValue = contentDesc.replace("%", "").trim().toIntOrNull()
            if (numericValue != null) return numericValue
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val found = findPercentage(child)
            child?.recycle()
            if (found != null) return found
        }
        return null
    }

    /**
     * Devuelve el primer nodo en profundidad cuyo contentDescription o text
     * satisface [pred]. El nodo devuelto es propiedad del caller; los
     * descendientes descartados se reciclan.
     */
    fun findFirstNodeMatching(
        node: AccessibilityNodeInfo?,
        pred: (String) -> Boolean
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = (node.contentDescription?.toString() ?: "") + "|" + (node.text?.toString() ?: "")
        if (pred(desc)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstNodeMatching(child, pred)
            if (found != null) {
                if (found !== child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    /**
     * Recolecta los ImageView sin contentDescription ni texto (posibles X de cierre).
     * Los nodos que entran a [out] no se reciclan aquí; el caller los recicla.
     * Retorna true si el nodo actual entró a [out].
     */
    fun collectImageViewCandidates(
        node: AccessibilityNodeInfo?,
        out: MutableList<AccessibilityNodeInfo>
    ): Boolean {
        if (node == null) return false

        val className = node.className?.toString() ?: ""
        if (className.contains("ImageView") &&
            node.contentDescription.isNullOrBlank() &&
            node.text.isNullOrBlank()
        ) {
            out.add(node)
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val kept = collectImageViewCandidates(child, out)
                if (!kept) child.recycle()
            }
        }
        return false
    }

    /** Clic en [target]: directo si es clickable, si no probando el padre. */
    fun clickNode(target: AccessibilityNodeInfo): Boolean {
        if (target.isClickable) return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val parent = target.parent
        var clicked = false
        if (parent != null) {
            if (parent.isClickable) clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            parent.recycle()
        }
        if (!clicked) clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return clicked
    }

    /**
     * Busca recursivamente el nodo cuyo contentDescription coincide exacto
     * y retorna sus coordenadas en pantalla. Null si no existe.
     */
    fun findNodeBoundsByContentDescription(
        node: AccessibilityNodeInfo?,
        contentDescription: String
    ): Rect? {
        if (node == null) return null
        if (node.contentDescription?.toString() == contentDescription) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (!rect.isEmpty) return rect
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val found = findNodeBoundsByContentDescription(child, contentDescription)
            child?.recycle()
            if (found != null) return found
        }
        return null
    }

    /**
     * Construye una representación visual del árbol de nodos para el Logcat.
     */
    fun generateTreeLog(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null) return
        val indent = "  ".repeat(depth)
        val className = node.className?.toString()?.split(".")?.last() ?: "Unknown"
        val id = node.viewIdResourceName?.split("/")?.last() ?: "no-id"
        val text = node.text?.toString()?.replace("\n", " ") ?: ""
        val contentDesc = node.contentDescription?.toString()?.replace("\n", " ") ?: ""

        sb.append("${indent}╠═ [$className] ID: $id | Text: \"$text\" | CD: \"$contentDesc\"\n")

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            generateTreeLog(child, sb, depth + 1)
            child?.recycle()
        }
    }

    fun logginTree(rootNode: AccessibilityNodeInfo, packageName: String = "PICAP") {
        val treeBuilder = StringBuilder()
        treeBuilder.append("\n╔════════════ ARBOL DE NODOS (${packageName}) ════════════╗\n")
        generateTreeLog(rootNode, treeBuilder, 0)
        treeBuilder.append("╚═════════════════════════════════════════════════╝")
        Log.d(SERVICE_TAG, treeBuilder.toString())
    }

    /**
     * Retorna todas las ventanas que coincidan con el packageName dado.
     */
    fun findAllWindows(windows: List<AccessibilityWindowInfo>, packageName: String): List<AccessibilityNodeInfo> {
        return try {
            windows.mapNotNull { window ->
                try {
                    window.root?.takeIf { it.packageName == packageName }
                } catch (e: Exception) {
                    Log.w(SERVICE_TAG, "findAllWindows: window root null o inaccesible: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(SERVICE_TAG, "findAllWindows: error accediendo windows: ${e.message}")
            emptyList()
        }
    }
}
