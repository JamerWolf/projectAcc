package com.example.projectacc.whatsapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.projectacc.OrderStateManager
import com.example.projectacc.SERVICE_TAG
import com.example.projectacc.a11y.AccessibilityTree
import com.example.projectacc.eval.AutoAcceptConfig
import com.example.projectacc.eval.evaluateAutoAccept
import com.example.projectacc.floating.FloatingPopupManager
import com.example.projectacc.parser.WhatsAppParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full WhatsApp pipeline: tree extraction, background parsing, delayed
 * auto-accept and paste/send on the text input.
 *
 * @param scope the service scope (serviceScope); never create another scope.
 * @param rootProvider returns rootInActiveWindow at call time.
 * @param floatingPopupProvider returns the current FloatingPopupManager (null until onCreate).
 */
@Suppress("DEPRECATION")
class WhatsAppResponder(
    private val scope: CoroutineScope,
    private val context: Context,
    private val rootProvider: () -> AccessibilityNodeInfo?,
    private val floatingPopupProvider: () -> FloatingPopupManager?,
) {

    fun handleWhatsAppEvent(event: AccessibilityEvent) {
        val rootNode = rootProvider() ?: return

        // Extract text on main thread (fast - just reads node properties)
        val fullText = AccessibilityTree.extractAllText(rootNode)
        rootNode.recycle()

        // Process on background thread (parsing + popup)
        scope.launch {
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

        Log.d(SERVICE_TAG, "WHATSAPP: Procesando texto en background (${fullText.length} chars)")

        // 0. SKIP RESPONSE MESSAGES and PLATE REQUESTS
        val lowerText = fullText.lowercase()

        // Find the LAST message block to check for response/plate patterns
        // (don't check entire text - old messages above may trigger false positives)
        val lastMessageBlock = findLastMessageBlock(lowerText)

        if (lastMessageBlock.contains("placa")) {
            Log.d(SERVICE_TAG, "WHATSAPP: Último mensaje contiene 'placa'. Manejado por notificación.")
            return
        }

        val isResponse = responsePatterns.any { pattern -> lastMessageBlock.contains(pattern) }
        if (isResponse) {
            Log.d(SERVICE_TAG, "WHATSAPP: Último mensaje es respuesta. Ignorando parser de servicios.")
            return
        }

        // 1. AUTO-PLATE: Disabled here - handled by NotificationInterceptorService

        // 2. SERVICE MESSAGE: Only if at least one switch is enabled
        val autoRespondEnabled = OrderStateManager.isGroupAutoRespondEnabled.value
        val showPopupEnabled = OrderStateManager.isWhatsAppShowPopupEnabled.value

        if (!autoRespondEnabled && !showPopupEnabled) {
            Log.d(SERVICE_TAG, "WHATSAPP: Ambos switches desactivados. Ignorando.")
            return
        }

        val service = WhatsAppParser.parse(fullText) ?: return

        // Check if already scanned
        val scannedIds = OrderStateManager.scannedWhatsAppServiceIds.value
        if (scannedIds.contains(service.id)) {
            Log.d(SERVICE_TAG, "WHATSAPP: Servicio #${service.id} ya escaneado. Ignorando.")
            return
        }

        // New service detected
        Log.d(SERVICE_TAG, "WHATSAPP: Servicio detectado #${service.id} - ${service.empresa}")
        OrderStateManager.setWhatsAppOrder(service)
        OrderStateManager.addScannedWhatsAppServiceId(service.id)

        // Check auto-accept conditions (same evaluator as Picap)
        val valor = service.extractValor() ?: 0
        val config = AutoAcceptConfig(
            OrderStateManager.whatsappAutoAcceptMinGanancia1.value.toInt(),
            OrderStateManager.whatsappAutoAcceptMinGanancia2.value.toInt(),
            OrderStateManager.whatsappAutoAcceptMaxKmCond2.value,
            OrderStateManager.isWhatsappAutoAcceptByKmEnabled.value,
            OrderStateManager.whatsappAutoAcceptMaxKm.value
        )

        // Extract km from service (WhatsApp services may have km in origen field)
        val kmRecogida = extractKmFromWhatsApp(service.origen)

        // Check if service meets auto-accept conditions
        // km desconocido adopta el centinela de Picap (999.0): con umbral al
        // máximo (>= 5.0) la condición 3 acepta aunque no se conozcan los km.
        val meetsConditions = evaluateAutoAccept(valor, kmRecogida ?: 999.0, config).accepted

        // Auto-respond if enabled AND conditions are met
        if (autoRespondEnabled && meetsConditions) {
            val delayMs = OrderStateManager.whatsappAutoAcceptDelayMs.value
            val delayEnabled = OrderStateManager.isWhatsAppAutoAcceptDelayEnabled.value
            val effectiveDelay = if (delayEnabled) delayMs else 0L
            val detectedAt = System.currentTimeMillis()

            Log.d(
                SERVICE_TAG,
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
        if (showPopupEnabled && floatingPopupProvider()?.canDrawOverlays() == true) {
            withContext(Dispatchers.Main) {
                floatingPopupProvider()?.show(service, onAccept = { acceptedService ->
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

    /**
     * Paste only - pega texto SIN enviar. Para que el usuario revise y envíe manual.
     */
    fun pasteOnly(text: String) {
        Log.d(SERVICE_TAG, "WHATSAPP: pasteOnly called: $text")
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("whatsapp_response", text)
            clipboard.setPrimaryClip(clip)

            val rootNode = rootProvider() ?: return

            val inputNode = AccessibilityTree.findEditText(rootNode)
            if (inputNode != null) {
                inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val pasteBundle = Bundle().apply {
                    putBoolean(
                        "android.view.accessibility.accessibilityNodeInfo.actionArguments.pasteKey",
                        true
                    )
                }
                inputNode.performAction(AccessibilityNodeInfo.ACTION_PASTE, pasteBundle)
                Log.d(SERVICE_TAG, "WHATSAPP: Texto pegado (sin enviar): $text")
            } else {
                Log.w(SERVICE_TAG, "WHATSAPP: Campo de texto no encontrado en pasteOnly")
            }
            rootNode.recycle()
        } catch (e: Exception) {
            Log.e(SERVICE_TAG, "WHATSAPP: Error en pasteOnly: ${e.message}")
        }
    }

    /**
     * Copies text to clipboard, then performs paste + send on the current WhatsApp input.
     * Uses retry mechanism for cases where multiple messages come in simultaneously.
     */
    fun pasteAndSend(text: String, notificationTimestamp: Long = 0L) {
        Log.d(SERVICE_TAG, "WHATSAPP: pasteAndSend called: $text")
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("whatsapp_response", text)
            clipboard.setPrimaryClip(clip)

            val rootNode = rootProvider() ?: return

            val inputNode = AccessibilityTree.findEditText(rootNode)
            if (inputNode != null) {
                inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val pasteBundle = Bundle().apply {
                    putBoolean(
                        "android.view.accessibility.accessibilityNodeInfo.actionArguments.pasteKey",
                        true
                    )
                }
                inputNode.performAction(AccessibilityNodeInfo.ACTION_PASTE, pasteBundle)
                Log.d(SERVICE_TAG, "WHATSAPP: Texto pegado: $text")

                // Increased delay and added retry for send button
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                var attempts = 0
                val maxAttempts = 3

                val retryRunnable = object : Runnable {
                    override fun run() {
                        attempts++
                        val root = rootProvider() ?: return
                        val sendBtn = findSendButton(root)
                        if (sendBtn != null) {
                            sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            val elapsed =
                                if (notificationTimestamp > 0) System.currentTimeMillis() - notificationTimestamp else 0L
                            Log.d(SERVICE_TAG, "WHATSAPP: Mensaje enviado (intento #$attempts): $text")
                            if (notificationTimestamp > 0) {
                                Log.i(SERVICE_TAG, "WHATSAPP: ⏱️ Tiempo notificación → envío: ${elapsed}ms")
                            }
                            sendBtn.recycle()
                            root.recycle()
                        } else {
                            root.recycle()
                            if (attempts < maxAttempts) {
                                Log.d(
                                    SERVICE_TAG,
                                    "WHATSAPP: Boton de envio no encontrado, reintento #$attempts"
                                )
                                handler.postDelayed(this, 300)
                            } else {
                                Log.w(
                                    SERVICE_TAG,
                                    "WHATSAPP: Boton de envio no encontrado despues de $maxAttempts intentos"
                                )
                            }
                        }
                    }
                }

                handler.postDelayed(retryRunnable, 200)
            } else {
                Log.w(SERVICE_TAG, "WHATSAPP: Campo de texto no encontrado")
            }
            rootNode.recycle()
        } catch (e: Exception) {
            Log.e(SERVICE_TAG, "WHATSAPP: Error en pasteAndSend: ${e.message}")
        }
    }

    /**
     * Checks if there's a text input field in the current window.
     */
    fun hasTextInput(): Boolean {
        val rootNode = rootProvider() ?: return false
        val found = AccessibilityTree.findEditText(rootNode)
        rootNode.recycle()
        return found != null
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
}
