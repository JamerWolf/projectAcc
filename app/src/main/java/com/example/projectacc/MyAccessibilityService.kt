package com.example.projectacc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
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
import kotlin.math.log
import kotlin.math.roundToInt

/**
 * Modelo de datos para representar una orden de Picap capturada.
 */
data class PicapOrder(
    val id: String = "",
    val ganancia: String = "",
    val tiempoRecogida: String = "",
    val direccionRecogida: String = "",
    val tiempoEntrega: String = "",
    val direccionEntrega: String = "",
    val servicio: String = "",
    val kmRecogida: Double = 0.0,
    val kmEntrega: Double = 0.0
)

@Suppress("DEPRECATION")
class MyAccessibilityService : AccessibilityService() {
    private val TAG = "MyAccessibilityService"

    companion object {
        var instance: MyAccessibilityService? = null
            private set

        const val PACKAGE_PICAP = "co.picap.passenger"
        const val PACKAGE_WHATSAPP = "com.whatsapp"
        const val PACKAGE_WHATSAPP_BIZ = "com.whatsapp.w4b"
    }

    private var floatingPopup: FloatingPopupManager? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Paste only - pega texto SIN enviar. Para que el usuario revise y envíe manual.
     */
    fun pasteOnly(text: String) {
        Log.d(TAG, "WHATSAPP: pasteOnly called: $text")
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("whatsapp_response", text)
            clipboard.setPrimaryClip(clip)

            val rootNode = rootInActiveWindow ?: return

            val inputNode = findEditText(rootNode)
            if (inputNode != null) {
                inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val pasteBundle = Bundle().apply {
                    putBoolean(
                        "android.view.accessibility.accessibilityNodeInfo.actionArguments.pasteKey",
                        true
                    )
                }
                inputNode.performAction(AccessibilityNodeInfo.ACTION_PASTE, pasteBundle)
                Log.d(TAG, "WHATSAPP: Texto pegado (sin enviar): $text")
            } else {
                Log.w(TAG, "WHATSAPP: Campo de texto no encontrado en pasteOnly")
            }
            rootNode.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "WHATSAPP: Error en pasteOnly: ${e.message}")
        }
    }

    // Almacena el último porcentaje detectado para evitar logs repetitivos de la misma barra
    private var lastPercentage: Int = -1

    // Almacena la última orden detectada para evitar duplicados por ID
    private var lastOrder: PicapOrder? = null

    // Controla si ya se escaneó la lista al iniciar el auto-clic
    private var hasScannedInitially = false

    // Cooldown para evitar clics repetidos en auto-accept
    private var lastClickTime: Long = 0L
    private val CLICK_COOLDOWN_MS = 2000L

    override fun onServiceConnected() {
        super.onServiceConnected()

        val info = serviceInfo

        info.flags = info.flags or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                0x10000000 or // FLAG_REQUEST_OVERLAY_WINDOWS (API 22+)
                AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT

        serviceInfo = info

        Log.d(TAG, "AccessibilityService conectado")
        Log.d(TAG, "flags = ${serviceInfo.flags}")
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        floatingPopup = FloatingPopupManager(this)
    }

    override fun onDestroy() {
        hideKmOverlay()
        serviceScope.cancel()
        floatingPopup?.dismiss()
        floatingPopup = null
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = when (event?.eventType) {
            1 -> "VIEW_CLICKED"
            2 -> "VIEW_LONG_CLICKED"
            4 -> "VIEW_SELECTED"
            8 -> "VIEW_FOCUSED"
            16 -> "VIEW_TEXT_CHANGED"
            32 -> "WINDOW_STATE_CHANGED"
            64 -> "NOTIFICATION_STATE_CHANGED"
            128 -> "VIEW_HOVER_ENTER"
            256 -> "VIEW_HOVER_EXIT"
            512 -> "TOUCH_EXPLORATION_GESTURE_START"
            1024 -> "TOUCH_EXPLORATION_GESTURE_END"
            2048 -> "WINDOW_CONTENT_CHANGED"
            4096 -> "VIEW_TEXT_SELECTION_CHANGED"
            8192 -> "VIEW_SCROLLED"
            16384 -> "VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY"
            32768 -> "GESTURE_DETECTION_START"
            65536 -> "GESTURE_DETECTION_END"
            131072 -> "TOUCH_INTERACTION_START"
            262144 -> "TOUCH_INTERACTION_END"
            524288 -> "WINDOWS_CHANGED"
            1048576 -> "VIEW_CONTEXT_CLICKED"
            2097152 -> "ASSIST_READING_CONTEXT"
            else -> "TYPE_${event?.eventType}"
        }
        Log.d(
            "EVENT_DEBUG",
            "TYPE=${type} " +
                    "PACKAGE=${event?.packageName} " +
                    "CLASS=${event?.className} " +
                    "TEXT=${event?.text}"
        )

        logWindows()

        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        // === PICAP HANDLING ===
        if (packageName == PACKAGE_PICAP) {
            handlePicapEvent(event)
            return
        }

        // === WHATSAPP HANDLING ===
        if (packageName == PACKAGE_WHATSAPP || packageName == PACKAGE_WHATSAPP_BIZ) {
            handleWhatsAppEvent(event)
            return
        }
    }

    private fun logWindows() {
        Log.d("DEBUG_WINDOWS", "==========WINDOWS (${windows.size})=============")
        windows.forEach { window ->
            try {
                val type = when (window.type) {
                    1 -> "APPLICATION"
                    2 -> "INPUT_METHOD"
                    3 -> "SYSTEM"
                    4 -> "ACCESSIBILITY_OVERLAY"
                    5 -> "SPLIT_SCREEN_DIVIDER"
                    6 -> "MAGNIFICATION_OVERLAY"
                    else -> "TYPE_${window.type}"
                }

                val root = window.root

                val pkg = root?.packageName?.toString() ?: "ROOT_NULL"

                Log.d(
                    "DEBUG_WINDOWS",
                    "Window: " +
                            "id=${window.id} " +
                            "type=$type(${window.type}) " +
                            "pkg=$pkg " +
                            "root=${root != null} " +
                            "active=${window.isActive} " +
                            "focused=${window.isFocused} " +
                            "layer=${window.layer}"
                )

            } catch (e: Exception) {
                Log.w(
                    "DEBUG_WINDOWS",
                    "Error procesando window: ${e.message}"
                )
            }
        }
        Log.d("DEBUG_WINDOWS", "===========================================")
    }

    /**
     * Busca entre todas las ventanas la que coincida con el packageName dado
     * y retorna su rootNode. Retorna null si no se encuentra.
     */
    private fun findWindowRoot(packageName: String): AccessibilityNodeInfo? {
        return windows.firstOrNull {
            it.root?.packageName == packageName
        }?.root
    }

    /**
     * Retorna todas las ventanas que coincidan con el packageName dado.
     */
    private fun findAllWindows(packageName: String): List<AccessibilityNodeInfo> {
        return try {
            windows.mapNotNull { window ->
                try {
                    window.root?.takeIf { it.packageName == packageName }
                } catch (e: Exception) {
                    Log.w(TAG, "findAllWindows: window root null o inaccesible: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "findAllWindows: error accediendo windows: ${e.message}")
            emptyList()
        }
    }

    /**
     * Handles windows from unknown packages that might be Picap popups
     * triggered by notification clicks when user is outside Picap.
     */
    private fun handlePossiblePicapPopup(event: AccessibilityEvent) {

    }

    private fun handlePicapEvent(event: AccessibilityEvent) {
        val picapWindows = findAllWindows(PACKAGE_PICAP)
        if (picapWindows.isEmpty()) {
            // Sin ventanas de Picap visibles: el popup de servicio ya no existe
            hideKmOverlay()
            return
        }

        // --- MODO AUTO-CLIC EN LISTA ---
        if (OrderStateManager.isAutoClickEnabled.value) {
            for (rootNode in picapWindows) {
                if (findAndClickEstimatedPrice(rootNode)) {
                    Log.d(TAG, "AUTOCLICK: Orden capturada!")
                    hasScannedInitially = false
                    picapWindows.forEach { it.recycle() }
                    return
                }
            }
        }

        // Buscar orden válida en alguna de las ventanas
        for (rootNode in picapWindows) {
            val currentPercentage = findPercentage(rootNode)

            // Lógica de filtrado por porcentaje:
            if (currentPercentage != null) {
                if (currentPercentage < lastPercentage) {
                    lastPercentage = currentPercentage
                    rootNode.recycle()
                    continue
                }
                lastPercentage = currentPercentage
            } else {
                lastPercentage = -1
            }

            // Procesar ventana y verificar si tiene orden válida
            if (processPicapWindow(rootNode)) {
                picapWindows.forEach { it.recycle() }
                return
            }
            rootNode.recycle()
        }
    }

    /**
     * Procesa una ventana de Picap: genera log, extrae orden, y evalúa auto-accept.
     * Retorna true si se procesó una orden válida.
     */
    private fun processPicapWindow(rootNode: AccessibilityNodeInfo): Boolean {
        // 1. GENERAR EL LOG VISUAL DEL ÁRBOL
        logginTree(rootNode)

        // 2. EXTRAER DATOS PARA EL MODELO ORDER
        val nodesContent = mutableListOf<String>()
        flattenContentDescriptions(rootNode, nodesContent)
        val order = parseOrder(nodesContent)

        // Si no hay ID, no es una orden válida
        if (order.id.isEmpty()) {
            hideKmOverlay()
            return false
        }

        // --- OVERLAY: km totales junto al nodo del precio ---
        // Se actualiza en cada evento (misma orden o nueva) para refrescar posición/texto.
        updateKmOverlay(rootNode, order)

        // --- AUTO-ACCEPT: Evaluar siempre que haya ID, sin importar si es la misma orden ---
        if (OrderStateManager.isPicapAutoAcceptEnabled.value && shouldAutoAccept(order)) {
            val now = System.currentTimeMillis()
            if (now - lastClickTime >= CLICK_COOLDOWN_MS) {
                Log.d(TAG, "AUTO-ACCEPT: Orden califica para auto-acept. Buscando botón...")
                if (findAndClickAcceptButton(rootNode)) {
                    lastClickTime = System.currentTimeMillis()
                    Log.i(TAG, "AUTO-ACCEPT: Orden auto-aceptada! No se mostrara en UI.")
                    hideKmOverlay()
                    return true
                }
            } else {
                Log.d(TAG, "AUTO-ACCEPT: En cooldown, esperando...")
            }
        }

        // Actualizar UI solo si es una orden nueva basada en el ID
        if (order.id != lastOrder?.id) {
            lastOrder = order
            OrderStateManager.setOrder(order)

            val summary = """
                
                ORDEN CAPTURADA [#${order.id}]:
                Ganancia: ${order.ganancia}
                Recogida: ${order.direccionRecogida} (${order.tiempoRecogida})
                Entrega:  ${order.direccionEntrega} (${order.tiempoEntrega})
            """.trimIndent()
            Log.i(TAG, summary)
            return true
        }

        return false
    }

    private fun logginTree(rootNode: AccessibilityNodeInfo, packageName: String = "PICAP") {
        val treeBuilder = StringBuilder()
        treeBuilder.append("\n╔════════════ ARBOL DE NODOS (${packageName}) ════════════╗\n")
        generateTreeLog(rootNode, treeBuilder, 0)
        treeBuilder.append("╚═════════════════════════════════════════════════╝")
        Log.d(TAG, treeBuilder.toString())
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

    // Patterns that indicate this is a RESPONSE message (not a service offer)
    private val responsePatterns =
        listOf("genial", "asignarte", "envíame la placa", "enviame la placa")

    /**
     * Extracts the last message block from the full text.
     * Finds the last ⚡ or "Nuevo servicio" and returns text from there.
     */
    private fun findLastMessageBlock(text: String): String {
        val lastLightning = text.lastIndexOf("⚡")
        val lastNuevoServicio = text.lastIndexOf("nuevo servicio")
        val lastMeInteresa = text.lastIndexOf("me interesa")

        val markers = listOf(lastLightning, lastNuevoServicio, lastMeInteresa).filter { it >= 0 }
        return if (markers.isNotEmpty()) {
            text.substring(markers.max())
        } else {
            text
        }
    }

    private suspend fun processWhatsAppText(fullText: String) {
        if (fullText.isEmpty()) return

        Log.d(TAG, "WHATSAPP: Procesando texto en background (${fullText.length} chars)")

        // 0. SKIP RESPONSE MESSAGES and PLATE REQUESTS
        val lowerText = fullText.lowercase()

        // Find the LAST message block to check for response/plate patterns
        // (don't check entire text - old messages above may trigger false positives)
        val lastMessageBlock = findLastMessageBlock(lowerText)

        if (lastMessageBlock.contains("placa")) {
            Log.d(TAG, "WHATSAPP: Último mensaje contiene 'placa'. Manejado por notificación.")
            return
        }

        val isResponse = responsePatterns.any { pattern -> lastMessageBlock.contains(pattern) }
        if (isResponse) {
            Log.d(TAG, "WHATSAPP: Último mensaje es respuesta. Ignorando parser de servicios.")
            return
        }

        // 1. AUTO-PLATE: Disabled here - handled by NotificationInterceptorService

        // 2. SERVICE MESSAGE: Only if at least one switch is enabled
        val autoRespondEnabled = OrderStateManager.isGroupAutoRespondEnabled.value
        val showPopupEnabled = OrderStateManager.isWhatsAppShowPopupEnabled.value

        if (!autoRespondEnabled && !showPopupEnabled) {
            Log.d(TAG, "WHATSAPP: Ambos switches desactivados. Ignorando.")
            return
        }

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

        // Check auto-accept conditions (same as Picap)
        val valor = service.extractValor() ?: 0
        val minGanancia1 = OrderStateManager.whatsappAutoAcceptMinGanancia1.value
        val minGanancia2 = OrderStateManager.whatsappAutoAcceptMinGanancia2.value
        val maxKmCond2 = OrderStateManager.whatsappAutoAcceptMaxKmCond2.value
        val isKmEnabled = OrderStateManager.isWhatsappAutoAcceptByKmEnabled.value
        val maxKm = OrderStateManager.whatsappAutoAcceptMaxKm.value

        // Extract km from service (WhatsApp services may have km in origen field)
        val kmRecogida = extractKmFromWhatsApp(service.origen)

        // Check if service meets auto-accept conditions
        val meetsConditions = valor >= minGanancia1 ||
                (kmRecogida != null && kmRecogida <= maxKmCond2 && valor >= minGanancia2) ||
                (isKmEnabled && kmRecogida != null && (maxKm >= 5.0 || kmRecogida <= maxKm))

        // Auto-respond if enabled AND conditions are met
        if (autoRespondEnabled && meetsConditions) {
            val delayMs = OrderStateManager.whatsappAutoAcceptDelayMs.value
            val delayEnabled = OrderStateManager.isWhatsAppAutoAcceptDelayEnabled.value
            val effectiveDelay = if (delayEnabled) delayMs else 0L
            val detectedAt = System.currentTimeMillis()

            Log.d(
                TAG,
                "WHATSAPP: Auto-aceptar: valor $valor, km $kmRecogida, delay ${effectiveDelay}ms"
            )
            if (effectiveDelay > 0) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    pasteAndSend("Me interesa ${service.id}", detectedAt)
                }, effectiveDelay)
            } else {
                pasteAndSend("Me interesa ${service.id}", detectedAt)
            }
            return
        }

        // Show floating popup if enabled (post to main thread for UI)
        // Auto-accept popup ONLY if auto-respond switch is enabled AND conditions are met
        if (showPopupEnabled && floatingPopup?.canDrawOverlays() == true) {
            withContext(Dispatchers.Main) {
                floatingPopup?.show(service, onAccept = { acceptedService ->
                    pasteAndSend("Me interesa ${acceptedService.id}", System.currentTimeMillis())
                }, autoAccept = autoRespondEnabled && meetsConditions)
            }
        }
    }

    /**
     * Extracts km from WhatsApp service origen field.
     * Example: "Puerto Colombia (4.2 km)"
     * Returns km value or null if not found.
     */
    private fun extractKmFromWhatsApp(origen: String): Double? {
        val match = Regex("([\\d.,]+)\\s*km", RegexOption.IGNORE_CASE).find(origen)
        return match?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()
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
     * Uses retry mechanism for cases where multiple messages come in simultaneously.
     */
    fun pasteAndSend(text: String, notificationTimestamp: Long = 0L) {
        Log.d(TAG, "WHATSAPP: pasteAndSend called: $text")
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("whatsapp_response", text)
            clipboard.setPrimaryClip(clip)

            val rootNode = rootInActiveWindow ?: return

            val inputNode = findEditText(rootNode)
            if (inputNode != null) {
                inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val pasteBundle = Bundle().apply {
                    putBoolean(
                        "android.view.accessibility.accessibilityNodeInfo.actionArguments.pasteKey",
                        true
                    )
                }
                inputNode.performAction(AccessibilityNodeInfo.ACTION_PASTE, pasteBundle)
                Log.d(TAG, "WHATSAPP: Texto pegado: $text")

                // Increased delay and added retry for send button
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                var attempts = 0
                val maxAttempts = 3

                val retryRunnable = object : Runnable {
                    override fun run() {
                        attempts++
                        val root = rootInActiveWindow ?: return
                        val sendBtn = findSendButton(root)
                        if (sendBtn != null) {
                            sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            val elapsed =
                                if (notificationTimestamp > 0) System.currentTimeMillis() - notificationTimestamp else 0L
                            Log.d(TAG, "WHATSAPP: Mensaje enviado (intento #$attempts): $text")
                            if (notificationTimestamp > 0) {
                                Log.i(TAG, "WHATSAPP: ⏱️ Tiempo notificación → envío: ${elapsed}ms")
                            }
                            sendBtn.recycle()
                            root.recycle()
                        } else {
                            root.recycle()
                            if (attempts < maxAttempts) {
                                Log.d(
                                    TAG,
                                    "WHATSAPP: Boton de envio no encontrado, reintento #$attempts"
                                )
                                handler.postDelayed(this, 300)
                            } else {
                                Log.w(
                                    TAG,
                                    "WHATSAPP: Boton de envio no encontrado despues de $maxAttempts intentos"
                                )
                            }
                        }
                    }
                }

                handler.postDelayed(retryRunnable, 200)
            } else {
                Log.w(TAG, "WHATSAPP: Campo de texto no encontrado")
            }
            rootNode.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "WHATSAPP: Error en pasteAndSend: ${e.message}")
        }
    }

    /**
     * Checks if there's a text input field in the current window.
     */
    fun hasTextInput(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val found = findEditText(rootNode)
        rootNode.recycle()
        return found != null
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

        // Exclude forward/reenviar buttons - they contain "Enviar" but are NOT the send button
        val isForwardButton = cd.contains("Reenviar", ignoreCase = true) ||
                cd.contains("Forward", ignoreCase = true) ||
                text.contains("Reenviar", ignoreCase = true) ||
                text.contains("Forward", ignoreCase = true)

        if (!isForwardButton) {
            // Check for send button by content description, text, or resource ID
            if (cd.contains("Enviar", ignoreCase = true) || cd.contains(
                    "Send",
                    ignoreCase = true
                ) ||
                text.contains("Enviar", ignoreCase = true) || text.contains(
                    "Send",
                    ignoreCase = true
                ) ||
                id.contains("send", ignoreCase = true) ||
                id.contains("com.whatsapp:id/send", ignoreCase = true)
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
    private fun flattenContentDescriptions(
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

            // Entrega: Detecta "X min(s) (X km)" que NO empieza por "A "
            if ((current.contains("min (") || current.contains("mins (")) && !current.startsWith("A ")) {
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
        // Servicio: texto aislado con el nombre (ej. "Cruz verde Mostrador").
        // Se excluyen las direcciones para evitar falsos positivos.
        val servicio = texts.firstOrNull {
            it.contains("cruz verde", ignoreCase = true) && it != dirRec && it != dirEnt
        } ?: ""
        // Km embebidos en los strings de tiempo: "A 9 mins (4.31 km)" / "12 min (3.4 km)".
        // extractKmFromPickup devuelve 999.0 como centinela de "no encontrado".
        val kmRec = extractKmFromPickup(tiempoRec).takeIf { it < 999.0 } ?: 0.0
        val kmEnt = extractKmFromPickup(tiempoEnt).takeIf { it < 999.0 } ?: 0.0
        return PicapOrder(id, ganancia, tiempoRec, dirRec, tiempoEnt, dirEnt, servicio, kmRec, kmEnt)
    }

    // ============================================================
    // OVERLAY DE KM TOTALES (junto al nodo del precio)
    // ============================================================

    private var kmOverlayTextView: TextView? = null
    private var kmOverlayAttached = false

    /**
     * Busca recursivamente el nodo cuyo contentDescription coincide exacto
     * y retorna sus coordenadas en pantalla. Null si no existe.
     */
    private fun findNodeBoundsByContentDescription(
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
     * Muestra/actualiza u oculta el overlay de km totales según el estado
     * de la orden actual y la posición del nodo del precio ("X.XXX COP").
     */
    private fun updateKmOverlay(rootNode: AccessibilityNodeInfo, order: PicapOrder) {
        val totalKm = order.kmRecogida + order.kmEntrega
        if (totalKm <= 0.0 || order.ganancia.isEmpty()) {
            hideKmOverlay()
            return
        }
        // El nodo del precio es el mismo del que se extrajo la ganancia
        val anchor = findNodeBoundsByContentDescription(rootNode, order.ganancia)
        if (anchor == null) {
            hideKmOverlay()
            return
        }
        showKmOverlay(anchor, totalKm)
    }

    private fun showKmOverlay(anchor: Rect, totalKm: Double) {
        try {
            if (!Settings.canDrawOverlays(this)) {
                Log.w(TAG, "KM_OVERLAY: permiso de overlay no concedido; no se muestra")
                return
            }
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val density = resources.displayMetrics.density
            val marginPx = (8 * density).roundToInt()

            val tv = kmOverlayTextView ?: TextView(this).apply {
                background = GradientDrawable().apply {
                    cornerRadius = 16f * density
                    setColor(0xE6121212.toInt())
                    setStroke((1.5f * density).roundToInt().coerceAtLeast(1), 0xFFA855F7.toInt())
                }
                setTextColor(Color.WHITE)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(
                    (10 * density).roundToInt(),
                    (6 * density).roundToInt(),
                    (10 * density).roundToInt(),
                    (6 * density).roundToInt()
                )
                elevation = 6f * density
                kmOverlayTextView = this
            }
            tv.text = "📏 ${String.format("%.1f", totalKm)} km"

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = anchor.right + marginPx
            }

            // Centrar verticalmente respecto al precio
            tv.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            params.y = (anchor.centerY() - tv.measuredHeight / 2).coerceAtLeast(0)

            if (kmOverlayAttached) {
                wm.updateViewLayout(tv, params)
            } else {
                wm.addView(tv, params)
                kmOverlayAttached = true
                Log.d(
                    TAG,
                    "KM_OVERLAY: visible total=${String.format("%.1f", totalKm)} km, precio en ${anchor.toShortString()}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "KM_OVERLAY: error mostrando overlay: ${e.message}")
        }
    }

    private fun hideKmOverlay() {
        if (!kmOverlayAttached) return
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            kmOverlayTextView?.let { wm.removeViewImmediate(it) }
            Log.d(TAG, "KM_OVERLAY: ocultado")
        } catch (e: Exception) {
            Log.w(TAG, "KM_OVERLAY: error al ocultar: ${e.message}")
        }
        kmOverlayAttached = false
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
        // Filtro de tipo de pedido (selector en la pestaña Picap): OMS / Mostrador / Todos.
        // Si el filtro excluye este tipo, no se auto-acepta sin importar ganancia ni km.
        val serviceFilter = OrderStateManager.picapAutoAcceptFilter.value
        val isMostrador = order.servicio.contains("Mostrador", ignoreCase = true)
        if ((serviceFilter == "MOSTRADOR" && !isMostrador) || (serviceFilter == "OMS" && isMostrador)) {
            Log.d(
                TAG,
                "AUTO-ACCEPT: Filtro $serviceFilter excluye servicio '${order.servicio}'. No aceptando."
            )
            return false
        }

        val gananciaNum = order.ganancia
            .replace("COP", "")
            .replace(" ", "")
            .replace(".", "")
            .toIntOrNull() ?: 0

        val kmRecogida = extractKmFromPickup(order.tiempoRecogida)
        val autoAcceptKm = OrderStateManager.autoAcceptMaxKm.value
        val minGanancia1 = OrderStateManager.autoAcceptMinGanancia1.value.toInt()
        val minGanancia2 = OrderStateManager.autoAcceptMinGanancia2.value.toInt()
        val maxKmCond2 = OrderStateManager.autoAcceptMaxKmCond2.value

        // CONDICIÓN 1: Ganancia >= umbral 1 → ACEPTAR SIEMPRE
        if (gananciaNum >= minGanancia1) {
            Log.d(
                TAG,
                "AUTO-ACCEPT: Condición 1 - Ganancia $gananciaNum >= $minGanancia1. Aceptando."
            )
            return true
        }

        // CONDICIÓN 2: Ganancia >= umbral 2 Y km <= km máximo condición 2 → ACEPTAR
        if (gananciaNum >= minGanancia2 && kmRecogida <= maxKmCond2) {
            Log.d(
                TAG,
                "AUTO-ACCEPT: Condición 2 - Ganancia $gananciaNum >= $minGanancia2 Y km $kmRecogida <= $maxKmCond2. Aceptando."
            )
            return true
        }

        // CONDICIÓN 3: km <= umbral seleccionado → ACEPTAR (solo si el switch está activo)
        if (OrderStateManager.isAutoAcceptByKmEnabled.value) {
            if (autoAcceptKm >= 5.0 || kmRecogida <= autoAcceptKm) {
                Log.d(
                    TAG,
                    "AUTO-ACCEPT: Condición 3 - umbral al máximo o km $kmRecogida <= umbral $autoAcceptKm. Aceptando."
                )
                return true
            }
        }

        Log.d(
            TAG,
            "AUTO-ACCEPT: No cumple ninguna condición (Ganancia: $gananciaNum, Km: $kmRecogida, Umbral: $autoAcceptKm)"
        )
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
