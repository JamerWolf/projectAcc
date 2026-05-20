package com.example.projectacc

import android.view.accessibility.AccessibilityEvent
import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Modelo de datos para representar una orden de Picap capturada.
 */
data class PicapOrder(
    val id: String = "",
    val ganancia: String = "",
    val tiempoRecogida: String = "",
    val direccionRecogida: String = "",
    val tiempoEntrega: String = "",
    val direccionEntrega: String = ""
)

@Suppress("DEPRECATION")
class MyAccessibilityService : AccessibilityService() {
    private val TAG = "MyAccessibilityService"
    
    // Almacena el último porcentaje detectado para evitar logs repetitivos de la misma barra
    private var lastPercentage: Int = -1

    // Almacena la última orden detectada para evitar duplicados por ID
    private var lastOrder: PicapOrder? = null

    // Controla si ya se escaneó la lista al iniciar el auto-clic
    private var hasScannedInitially = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null && event.packageName == "co.picap.passenger") {
            val rootNode = rootInActiveWindow ?: return

            // --- MODO AUTO-CLIC EN LISTA ---
            if (OrderStateManager.isAutoClickEnabled.value) {
                if (findAndClickEstimatedPrice(rootNode)) {
                    Log.d(TAG, "AUTOCLICK: Orden capturada! Desactivando modo auto-clic.")
                    OrderStateManager.setAutoClickEnabled(false)
                    hasScannedInitially = false
                    rootNode.recycle()
                    return
                }
            }
            
            val currentPercentage = findPercentage(rootNode)

            // Lógica de filtrado por porcentaje:
            if (currentPercentage != null) {
                if (currentPercentage < lastPercentage) {
                    lastPercentage = currentPercentage
                    rootNode.recycle()
                    return 
                }
                lastPercentage = currentPercentage
            } else {
                lastPercentage = -1
            }

            // 1. GENERAR EL LOG VISUAL DEL ÁRBOL
            val treeBuilder = StringBuilder()
            treeBuilder.append("\n╔════════════ ARBOL DE NODOS (PICAP) ════════════╗\n")
            generateTreeLog(rootNode, treeBuilder, 0)
            treeBuilder.append("╚═════════════════════════════════════════════════╝")
            Log.d(TAG, treeBuilder.toString())

            // 2. EXTRAER DATOS PARA EL MODELO ORDER
            val nodesContent = mutableListOf<String>()
            flattenContentDescriptions(rootNode, nodesContent)
            val order = parseOrder(nodesContent)

            // Solo procesamos si es una orden nueva basada en el ID
            if (order.id.isNotEmpty() && order.id != lastOrder?.id) {
                
                // --- AUTO-ACCEPT: Si cumple condiciones, hacer clic en "Aceptar" ---
                if (shouldAutoAccept(order)) {
                    Log.d(TAG, "AUTO-ACCEPT: Orden califica para auto-acept. Buscando botón...")
                    if (findAndClickAcceptButton(rootNode)) {
                        Log.i(TAG, "AUTO-ACCEPT: ✅ Orden auto-aceptada! No se mostrará en UI.")
                        rootNode.recycle()
                        return  // No guardamos la orden en la UI porque ya se aceptó
                    }
                }
                
                // Si llegamos aquí, la orden no se auto-aceptó: guardarla en la UI normalmente
                lastOrder = order
                OrderStateManager.setOrder(order)

                val summary = """
                    
                    📦 ORDEN CAPTURADA [#${order.id}]:
                    💰 Ganancia: ${order.ganancia}
                    📍 Recogida: ${order.direccionRecogida} (${order.tiempoRecogida})
                    🏁 Entrega:  ${order.direccionEntrega} (${order.tiempoEntrega})
                """.trimIndent()
                Log.i(TAG, summary)
            }

            rootNode.recycle()
        }
    }

    /**
     * Busca recursivamente en el árbol de nodos un elemento cuyo contentDescription
     * contenga "Precio estimado" (orden en lista de Picap) y hace clic en él.
     * Intenta primero clic directo en el nodo; si no es cliqueable, intenta con el padre.
     * Evita hacer clic en servicios ya escaneados (memoria de IDs).
     *
     * @return true si se encontró y se hizo clic exitosamente.
     */
    private fun findAndClickEstimatedPrice(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        // --- ESCANEAR LISTA AL INICIAR (una sola vez cuando se activa el switch) ---
        if (!hasScannedInitially) {
            val currentIds = mutableSetOf<String>()
            extractAllServiceIds(node, currentIds)
            if (currentIds.isNotEmpty()) {
                OrderStateManager.setScannedServiceIds(currentIds)
                Log.d(TAG, "MEMORY: IDs guardados al iniciar: $currentIds")
            }
            hasScannedInitially = true
        }

        val cd = node.contentDescription?.toString() ?: ""
        if (cd.contains("Precio estimado")) {
            // Extraer el ID de este servicio
            val idMatch = Regex("ID: ([a-f0-9]+)", RegexOption.IGNORE_CASE).find(cd)
            val serviceId = idMatch?.groupValues?.get(1) ?: ""
            
            // --- VERIFICAR MEMORIA ANTES DE HACER CLIC ---
            val scannedIds = OrderStateManager.scannedServiceIds.value
            if (serviceId.isNotEmpty() && scannedIds.contains(serviceId)) {
                Log.d(TAG, "MEMORY: ID $serviceId ya está en memoria (servicio viejo). Ignorando.")
                // No hacer clic, continuar buscando en otros nodos
            } else {
                // ID nuevo o no se pudo extraer: hacer clic
                Log.d(TAG, "AUTOCLICK: Orden nueva detectada (ID: $serviceId). Intentando clic...")
                
                if (node.isClickable) {
                    val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (result) {
                        // Añadir a memoria después del clic exitoso
                        if (serviceId.isNotEmpty()) {
                            OrderStateManager.addScannedServiceId(serviceId)
                            Log.d(TAG, "MEMORY: ID $serviceId añadido a memoria tras clic exitoso.")
                        }
                        return true
                    }
                }
                
                // Intentar con el padre
                val parent = node.parent
                if (parent != null) {
                    if (parent.isClickable) {
                        val result = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (result) {
                            if (serviceId.isNotEmpty()) {
                                OrderStateManager.addScannedServiceId(serviceId)
                            }
                            parent.recycle()
                            return true
                        }
                    }
                    parent.recycle()
                }
            }
        }

        // Buscar recursivamente en hijos
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val found = findAndClickEstimatedPrice(child)
                child.recycle()
                if (found) return true
            }
        }
        return false
    }

    /**
     * Aplana el árbol en una lista de Content Descriptions para facilitar la búsqueda por posición.
     */
    private fun flattenContentDescriptions(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        val cd = node.contentDescription?.toString() ?: ""
        if (cd.isNotEmpty()) list.add(cd)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            flattenContentDescriptions(child, list)
            child?.recycle()
        }
    }

    /**
     * Analiza la lista de textos para extraer los campos del modelo PicapOrder.
     */
    private fun parseOrder(texts: List<String>): PicapOrder {
        var id = ""
        var ganancia = ""
        var tiempoRec = ""
        var dirRec = ""
        var tiempoEnt = ""
        var dirEnt = ""

        for (i in texts.indices) {
            val current = texts[i]
            
            // Extraer ID (formato "ID: 12345")
            if (current.startsWith("ID: ")) {
                id = current.replace("ID: ", "").trim()
            }
            
            // Ganancia: el valor está después de "Tu ganancia final"
            if (current == "Tu ganancia final" && i + 1 < texts.size) {
                ganancia = texts[i + 1]
            }
            
            // Recogida: Detecta "A X mins..."
            if (current.startsWith("A ") && current.contains("min") && i + 1 < texts.size) {
                tiempoRec = current
                dirRec = texts[i + 1]
            }

            // Entrega: Detecta "X min (X km)" que NO empieza por "A "
            if (current.contains("min (") && !current.startsWith("A ")) {
                tiempoEnt = current
                // La dirección puede estar en i+1 o en i+2 (si el ID se interpone)
                if (i + 1 < texts.size) {
                    if (texts[i + 1].startsWith("ID: ")) {
                        // El ID se interpuso: extraer ID aquí y la dirección está en i+2
                        id = texts[i + 1].replace("ID: ", "").trim()
                        if (i + 2 < texts.size) {
                            dirEnt = texts[i + 2]
                        }
                    } else {
                        dirEnt = texts[i + 1]
                    }
                }
            }
        }
        return PicapOrder(id, ganancia, tiempoRec, dirRec, tiempoEnt, dirEnt)
    }

    /**
     * Extrae todos los IDs de servicios de la lista actual de Picap.
     * Busca nodos con "Precio estimado" y extrae el ID del contentDescription.
     * Se usa al activar el switch para guardar la lista actual.
     */
    private fun extractAllServiceIds(node: AccessibilityNodeInfo?, ids: MutableSet<String>) {
        if (node == null) return
        
        val cd = node.contentDescription?.toString() ?: ""
        if (cd.contains("Precio estimado")) {
            // Buscar el patrón "ID: XXXXX" en el contentDescription
            val idMatch = Regex("ID: ([a-f0-9]+)", RegexOption.IGNORE_CASE).find(cd)
            if (idMatch != null) {
                val id = idMatch.groupValues[1]
                ids.add(id)
                Log.d(TAG, "MEMORY: Guardando ID de lista: $id")
            }
        }
        
        // Continuar buscando en hijos
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractAllServiceIds(child, ids)
                child.recycle()
            }
        }
    }

    /**
     * Busca recursivamente el valor numérico del porcentaje (ej: "99%") en el árbol.
     */
    private fun findPercentage(node: AccessibilityNodeInfo?): Int? {
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
     * Construye una representación visual del árbol de nodos para el Logcat.
     */
    private fun generateTreeLog(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
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

    // ============================================================
    // FUNCIONES DE AUTO-ACCEPT
    // ============================================================

    /**
     * Extrae los kilómetros de recogida de un string como "A 9 mins (4.31 km)"
     */
    private fun extractKmFromPickup(pickupText: String): Double {
        val match = Regex("\\(([\\d,\\.]+)\\s*km\\)").find(pickupText)
        return match?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull() ?: 999.0
    }

    /**
     * Decide si una orden debe ser auto-aceptada según las reglas:
     * 1. Si ganancia >= 18000 COP → ACEPTAR SIEMPRE (sin importar km)
     * 2. Si ganancia >= 14000 COP Y km de recogida <= 4.5 → ACEPTAR
     * 3. Si km <= umbral seleccionado por el usuario → ACEPTAR (sin importar precio)
     */
    private fun shouldAutoAccept(order: PicapOrder): Boolean {
        val gananciaNum = order.ganancia
            .replace("COP", "")
            .replace(" ", "")
            .replace(".", "")
            .toIntOrNull() ?: 0

        val kmRecogida = extractKmFromPickup(order.tiempoRecogida)
        val autoAcceptKm = OrderStateManager.autoAcceptMaxKm.value

        // CONDICIÓN 1: Ganancia >= 18.000 → ACEPTAR SIEMPRE
        if (gananciaNum >= 18000) {
            Log.d(TAG, "AUTO-ACCEPT: Condición 1 - Ganancia $gananciaNum >= 18000. Aceptando.")
            return true
        }

        // CONDICIÓN 2: Ganancia >= 14.000 Y km <= 4.5 → ACEPTAR
        if (gananciaNum >= 14000 && kmRecogida <= 4.5) {
            Log.d(TAG, "AUTO-ACCEPT: Condición 2 - Ganancia $gananciaNum >= 14000 Y km $kmRecogida <= 4.5. Aceptando.")
            return true
        }

        // CONDICIÓN 3: km <= umbral seleccionado → ACEPTAR (solo si el switch está activo)
        // O si el selector de distancia está al máximo (5.0), acepta sin límite.
        if (OrderStateManager.isAutoAcceptByKmEnabled.value) {
            if (autoAcceptKm >= 5.0 || kmRecogida <= autoAcceptKm) {
                Log.d(TAG, "AUTO-ACCEPT: Condición 3 - umbral al máximo o km $kmRecogida <= umbral $autoAcceptKm. Aceptando.")
                return true
            }
        }

        Log.d(TAG, "AUTO-ACCEPT: No cumple ninguna condición (Ganancia: $gananciaNum, Km: $kmRecogida, Umbral: $autoAcceptKm)")
        return false
    }

    /**
     * Busca el botón "Aceptar" en el popup de orden y hace clic.
     * Retorna true si logró hacer clic.
     */
    private fun findAndClickAcceptButton(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val className = node.className?.toString() ?: ""

        // Buscar botones que contengan "Aceptar" en texto o contentDescription
        val isAcceptButton = (className.contains("Button") || className.contains("TextView")) &&
                (text.contains("Aceptar", ignoreCase = true) || 
                 contentDesc.contains("Aceptar", ignoreCase = true))

        if (isAcceptButton) {
            Log.d(TAG, "AUTO-ACCEPT: Botón 'Aceptar' encontrado. Intentando clic...")
            if (node.isClickable) {
                val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (result) {
                    Log.d(TAG, "AUTO-ACCEPT: Clic exitoso en botón Aceptar!")
                    return true
                }
            }
            // Intentar con el padre
            val parent = node.parent
            if (parent != null && parent.isClickable) {
                val result = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                parent.recycle()
                if (result) return true
            }
        }

        // Buscar recursivamente en hijos
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val found = findAndClickAcceptButton(child)
                child.recycle()
                if (found) return true
            }
        }
        return false
    }

    // ============================================================
    // FIN FUNCIONES DE AUTO-ACCEPT
    // ============================================================

    override fun onInterrupt() {
        // Método requerido por la interfaz de AccessibilityService
    }
}
