package com.example.projectacc.picap

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.projectacc.OrderHistoryManager
import com.example.projectacc.OrderStateManager
import com.example.projectacc.SERVICE_TAG
import com.example.projectacc.a11y.AccessibilityTree
import com.example.projectacc.model.PicapOrder
import com.example.projectacc.parser.PicapParser

/**
 * Picap pipeline orchestration: walks the windows, filters by percentage,
 * delegates parse/policy/autoclick/close/overlay and records the order in the
 * OrderStateManager / OrderHistoryManager globals.
 */
@Suppress("DEPRECATION")
class PicapOfferProcessor(
    private val context: Context,
    private val picapWindows: () -> List<AccessibilityNodeInfo>,
    private val overlay: KmOverlay,
    private val policy: PicapAutoAcceptPolicy,
    private val autoclicker: PicapListAutoclicker,
    private val closer: PicapOfferCloser,
) {

    // Almacena el último porcentaje detectado para evitar logs repetitivos de la misma barra
    private var lastPercentage: Int = -1

    // Almacena la última orden detectada para evitar duplicados por ID
    private var lastOrder: PicapOrder? = null

    // Cooldown para evitar clics repetidos en auto-accept
    private var lastClickTime: Long = 0L
    private val CLICK_COOLDOWN_MS = 2000L

    fun handlePicapEvent(event: AccessibilityEvent) {
        val picapWindows = picapWindows()
        if (picapWindows.isEmpty()) {
            // Sin ventanas de Picap visibles: el popup de servicio ya no existe
            Log.d(SERVICE_TAG, "PICAP: evento tipo=${event.eventType} sin ventanas visibles; se ignora.")
            overlay.hideKmOverlay()
            return
        }
        Log.d(SERVICE_TAG, "PICAP: evento tipo=${event.eventType} con ${picapWindows.size} ventana(s).")

        // --- MODO AUTO-CLIC EN LISTA ---
        if (OrderStateManager.isAutoClickEnabled.value) {
            for (rootNode in picapWindows) {
                if (autoclicker.findAndClickEstimatedPrice(rootNode)) {
                    Log.d(SERVICE_TAG, "AUTOCLICK: Orden capturada!")
                    autoclicker.hasScannedInitially = false
                    picapWindows.forEach { it.recycle() }
                    return
                }
            }
        }

        // Buscar orden válida en alguna de las ventanas
        for (rootNode in picapWindows) {
            val currentPercentage = AccessibilityTree.findPercentage(rootNode)

            // Lógica de filtrado por porcentaje:
            if (currentPercentage != null) {
                if (currentPercentage < lastPercentage) {
                    val prevPercentage = lastPercentage
                    lastPercentage = currentPercentage
                    // Un popup de oferta real (ID + Aceptar) jamás se descarta por
                    // porcentaje: en modo app el popup vive dentro de la ventana
                    // principal de Picap y su porcentaje puede ser menor al último
                    // visto, lo que silenciaba todo el procesamiento de la oferta.
                    val markers = mutableListOf<String>()
                    AccessibilityTree.flattenContentDescriptions(rootNode, markers)
                    val isOfferPopup = markers.any { it.startsWith("ID: ") } &&
                            markers.any { it.contains("Aceptar") }
                    if (isOfferPopup) {
                        Log.i(SERVICE_TAG, "PICAP: porcentaje $currentPercentage < previo $prevPercentage pero es popup de oferta; se procesa igual.")
                    } else {
                        Log.i(SERVICE_TAG, "PICAP: ventana omitida por porcentaje ($currentPercentage < previo $prevPercentage).")
                        rootNode.recycle()
                        continue
                    }
                } else {
                    lastPercentage = currentPercentage
                }
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
        Log.d(SERVICE_TAG, "PICAP: sin orden nueva en esta pasada (tipo=${event.eventType}).")
    }

    /**
     * Procesa una ventana de Picap: genera log, extrae orden, y evalúa auto-accept.
     * Retorna true si se procesó una orden válida.
     */
    private fun processPicapWindow(rootNode: AccessibilityNodeInfo): Boolean {
        // 1. GENERAR EL LOG VISUAL DEL ÁRBOL
        AccessibilityTree.logginTree(rootNode)

        // 2. EXTRAER DATOS PARA EL MODELO ORDER
        val nodesContent = mutableListOf<String>()
        AccessibilityTree.flattenContentDescriptions(rootNode, nodesContent)
        val order = PicapParser.parseOrder(nodesContent)

        // Si no hay ID, no es una orden válida
        if (order.id.isEmpty()) {
            overlay.hideKmOverlay()
            Log.d(SERVICE_TAG, "PICAP: ventana sin ID de orden; no se procesa (servicio='${order.servicio}').")
            return false
        }

        // --- NO CRUZ VERDE: sin el nodo de servicio "Cruz Verde ..." (servicio vacío)
        // y con estructura de popup de oferta → agendar el cierre en 200 ms (tiempo de
        // render) y NO registrar la orden en la tarjeta ni en el historial.
        // Regla de producto: si no es Cruz Verde SIEMPRE se cierra; no se memorizan
        // ids de servicios ya cerrados (una oferta que reaparezca se vuelve a cerrar).
        if (order.servicio.isEmpty() && nodesContent.any { it.contains("Aceptar", ignoreCase = true) }) {
            if (order.id == closer.pendingCloseId) {
                Log.d(SERVICE_TAG, "NO-CV: orden #${order.id} ya tiene timer activo (pending=${closer.pendingCloseId}); no se re-agenda.")
            } else {
                closer.pendingCloseId = order.id
                Log.i(SERVICE_TAG, "NO-CV: orden #${order.id} sin nodo Cruz Verde; cierre agendado en ${NON_CV_CLOSE_DELAY_MS} ms (serviceScope).")
                closer.scheduleNonCvClose(order.id)
            }
            return false
        }

        // --- OVERLAY: km totales junto al nodo del precio ---
        // Se actualiza en cada evento (misma orden o nueva) para refrescar posición/texto.
        overlay.updateKmOverlay(rootNode, order)

        // --- AUTO-ACCEPT: Evaluar siempre que haya ID, sin importar si es la misma orden ---
        if (OrderStateManager.isPicapAutoAcceptEnabled.value && policy.shouldAutoAccept(order)) {
            val now = System.currentTimeMillis()
            if (now - lastClickTime >= CLICK_COOLDOWN_MS) {
                Log.d(SERVICE_TAG, "AUTO-ACCEPT: Orden califica para auto-acept. Buscando botón...")
                if (findAndClickAcceptButton(rootNode)) {
                    lastClickTime = System.currentTimeMillis()
                    Log.i(SERVICE_TAG, "AUTO-ACCEPT: Orden auto-aceptada! No se mostrara en UI.")
                    OrderHistoryManager.addOrder(order, context)
                    overlay.hideKmOverlay()
                    return true
                }
            } else {
                Log.d(SERVICE_TAG, "AUTO-ACCEPT: En cooldown, esperando...")
            }
        }

        // Actualizar UI solo si es una orden nueva basada en el ID
        if (order.id != lastOrder?.id) {
            lastOrder = order
            OrderStateManager.setOrder(order)
            OrderHistoryManager.addOrder(order, context)

            val summary = """
                
                ORDEN CAPTURADA [#${order.id}]:
                Ganancia: ${order.ganancia}
                Recogida: ${order.direccionRecogida} (${order.tiempoRecogida})
                Entrega:  ${order.direccionEntrega} (${order.tiempoEntrega})
            """.trimIndent()
            Log.i(SERVICE_TAG, summary)
            return true
        }

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
            Log.d(SERVICE_TAG, "AUTO-ACCEPT: Botón 'Aceptar' encontrado. Intentando clic...")
            if (node.isClickable) {
                val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (result) {
                    Log.d(SERVICE_TAG, "AUTO-ACCEPT: Clic exitoso en botón Aceptar!")
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
}
