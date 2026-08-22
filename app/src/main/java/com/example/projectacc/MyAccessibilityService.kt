package com.example.projectacc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.projectacc.floating.FloatingPopupManager
import com.example.projectacc.model.WhatsAppService
import com.example.projectacc.parser.WhatsAppParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    companion object {
        var instance: MyAccessibilityService? = null
            private set
    }

    private var floatingPopup: FloatingPopupManager? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Almacena el último porcentaje detectado para evitar logs repetitivos de la misma barra
    private var lastPercentage: Int = -1

    // Almacena la última orden detectada para evitar duplicados por ID
    private var lastOrder: PicapOrder? = null

    // Controla si ya se escaneó la lista al iniciar el auto-clic
    private var hasScannedInitially = false

    // Cooldown para evitar clics repetidos en auto-accept
    private var lastClickTime: Long = 0L
    private val CLICK_COOLDOWN_MS = 2000L

    // WhatsApp auto-plate cooldown
    private var lastAutoPlateTime: Long = 0L
    private val AUTO_PLATE_COOLDOWN_MS = 3000L

    // Plate request patterns for auto-plate
    private val plateRequestPatterns = listOf("placa", "vehiculo", "vehículo", "ascopec", "tarjeta de propietario")

    override fun onCreate() {
        super.onCreate()
        instance = this
        floatingPopup = FloatingPopupManager(this)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        floatingPopup?.dismiss()
        floatingPopup = null
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        // === PICAP HANDLING ===
        if (packageName == "co.picap.passenger") {
            handlePicapEvent(event)
            return
        }

        // === WHATSAPP HANDLING ===
        if (packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b") {
            handleWhatsAppEvent(event)
            return
        }
    }

    private fun handlePicapEvent(event: AccessibilityEvent) {
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

        // --- AUTO-ACCEPT: Evaluar siempre que haya ID, sin importar si es la misma orden ---
        if (order.id.isNotEmpty() && shouldAutoAccept(order)) {
            val now = System.currentTimeMillis()
            if (now - lastClickTime >= CLICK_COOLDOWN_MS) {
                Log.d(TAG, "AUTO-ACCEPT: Orden califica para auto-acept. Buscando botón...")
                if (findAndClickAcceptButton(rootNode)) {
                    lastClickTime = System.currentTimeMillis()
                    Log.i(TAG, "AUTO-ACCEPT: Orden auto-aceptada! No se mostrara en UI.")
                    rootNode.recycle()
                    return
                }
            } else {
                Log.d(TAG, "AUTO-ACCEPT: En cooldown, esperando...")
            }
        }

        // Actualizar UI solo si es una orden nueva basada en el ID
        if (order.id.isNotEmpty() && order.id != lastOrder?.id) {
            lastOrder = order
            OrderStateManager.setOrder(order)

            val summary = """
                
                ORDEN CAPTURADA [#${order.id}]:
                Ganancia: ${order.ganancia}
                Recogida: ${order.direccionRecogida} (${order.tiempoRecogida})
                Entrega:  ${order.direccionEntrega} (${order.tiempoEntrega})
            """.trimIndent()
            Log.i(TAG, summary)
        }

        rootNode.recycle()
    }

    private fun handleWhatsAppEvent(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return

        // Extract text on main thread (fast - just reads node properties)
        val fullText = extractAllText(rootNode)
        rootNode.recycle()

        // Process on background thread (parsing + popup)
        serviceScope.launch {
            processWhatsAppText(fullText)
        }
    }

    private suspend fun processWhatsAppText(fullText: String) {
        if (fullText.isEmpty()) return

        Log.d(TAG, "WHATSAPP: Procesando texto en background (${fullText.length} chars)")

        // Parse on background thread
        val service = WhatsAppParser.parse(fullText) ?: return

        // Check if already scanned
        val scannedIds = OrderStateManager.scannedWhatsAppServiceIds.value
        if (scannedIds.contains(service.id)) {
            Log.d(TAG, "WHATSAPP: Servicio #${service.id} ya escaneado. Ignorando.")
            return
        }

        // New service detected
        Log.d(TAG, "WHATSAPP: Servicio detectado #${service.id} - ${service.empresa}")
        OrderStateManager.setWhatsAppOrder(service)
        OrderStateManager.addScannedWhatsAppServiceId(service.id)

        // Show floating popup (post to main thread for UI)
        if (floatingPopup?.canDrawOverlays() == true) {
            withContext(Dispatchers.Main) {
                floatingPopup?.show(service) { acceptedService ->
                    pasteAndSend("Me interesa ${acceptedService.id}")
                }
            }
            return
        }

        // Auto-plate: detect plate request in private messages
        if (OrderStateManager.isAutoPlateEnabled.value) {
            val plate = OrderStateManager.vehiclePlate.value
            if (plate.isNotEmpty()) {
                val lowerText = fullText.lowercase()
                val isPlateRequest = plateRequestPatterns.any { pattern -> lowerText.contains(pattern) }
                if (isPlateRequest) {
                    val now = System.currentTimeMillis()
                    if (now - lastAutoPlateTime >= AUTO_PLATE_COOLDOWN_MS) {
                        Log.d(TAG, "WHATSAPP: Solicitud de placa detectada. Pegando placa: $plate")
                        lastAutoPlateTime = now
                        withContext(Dispatchers.Main) {
                            pasteAndSend(plate)
                        }
                    }
                }
            }
        }
    }

    private fun extractAllText(node: AccessibilityNodeInfo?): String {
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

    /**
     * Copies text to clipboard, then performs paste + send on the current WhatsApp input.
     */
    private fun pasteAndSend(text: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("whatsapp_response", text)
            clipboard.setPrimaryClip(clip)

            val rootNode = rootInActiveWindow ?: return

            // Find the text input field (WhatsApp uses EditText for message input)
            val inputNode = findEditText(rootNode)
            if (inputNode != null) {
                // Focus the input
                inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

                // Paste using clipboard
                val pasteBundle = Bundle().apply {
                    putBoolean("android.view.accessibility.accessibilityNodeInfo.actionArguments.pasteKey", true)
                }
                inputNode.performAction(AccessibilityNodeInfo.ACTION_PASTE, pasteBundle)

                Log.d(TAG, "WHATSAPP: Texto pegado: $text")

                // Small delay then click send
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val root = rootInActiveWindow ?: return@postDelayed
                    val sendBtn = findSendButton(root)
                    if (sendBtn != null) {
                        sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        Log.d(TAG, "WHATSAPP: Mensaje enviado: $text")
                        sendBtn.recycle()
                    } else {
                        Log.w(TAG, "WHATSAPP: Boton de envio no encontrado")
                    }
                    root.recycle()
                }, 300)
            } else {
                Log.w(TAG, "WHATSAPP: Campo de texto no encontrado")
            }
            rootNode.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "WHATSAPP: Error en pasteAndSend: ${e.message}")
        }
    }

    private fun findEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
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

    private fun findSendButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val cd = node.contentDescription?.toString() ?: ""
        val text = node.text?.toString() ?: ""
        val id = node.viewIdResourceName ?: ""

        if (cd.contains("Enviar", ignoreCase = true) || cd.contains("Send", ignoreCase = true) ||
            text.contains("Enviar", ignoreCase = true) || text.contains("Send", ignoreCase = true) ||
            id.contains("send", ignoreCase = true)
        ) {
            if (node.isClickable) return node
            // Try parent
            val parent = node.parent
            if (parent != null && parent.isClickable) {
                val result = parent
                parent.recycle()
                return result
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findSendButton(child)
            if (found != null) return found
            child.recycle()
        }
        return null
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
        val match = Regex("\\(([\\d,\\.]+)\\s*(km|m)\\)").find(pickupText)
        if (match != null) {
            val value = match.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return 999.0
            val unit = match.groupValues[2]
            return if (unit == "m") value / 1000.0 else value
        }
        return 999.0
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
