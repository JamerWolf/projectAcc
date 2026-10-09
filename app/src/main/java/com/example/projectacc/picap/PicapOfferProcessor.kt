package com.example.projectacc.picap

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.projectacc.OrderHistoryManager
import com.example.projectacc.OrderStateManager
import com.example.projectacc.SERVICE_TAG
import com.example.projectacc.a11y.AccessibilityTree
import com.example.projectacc.model.PicapOrder
import com.example.projectacc.parser.PicapParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TREE_DEBUG = false

/**
 * Snapshot de un nodo recolectado por el escaneo único: contentDescription
 * crudo (null preservado), text y bounds en pantalla. Es la materia prima que
 * KmOverlay antes buscaba con su propio recorrido del árbol.
 */
private class PicapNodeSnapshot(
    val contentDescription: String?,
    val text: String,
    val bounds: Rect,
)

/**
 * DTO producido por UN solo DFS en pre-orden sobre la raíz de una ventana de
 * Picap. Reemplaza los recorridos redundantes por decisión (porcentaje,
 * flatten de marcadores, flatten de parseo, bounds del overlay, búsqueda del
 * botón Aceptar): todas las entradas de decisión se leen de aquí.
 *
 * [acceptCandidates] conserva nodos VIVOS en orden de recorrido (el primer
 * candidato reproduce el primer-match del findAndClickAcceptButton original);
 * el dueño debe llamar a [recycle] exactamente una vez.
 */
@Suppress("DEPRECATION")
private class PicapScanResult(
    private val rootNode: AccessibilityNodeInfo,
    val contentDescriptions: List<String>,
    val percentage: Int?,
    private val nodes: List<PicapNodeSnapshot>,
    val acceptCandidates: List<AccessibilityNodeInfo>,
) {
    /**
     * Primer nodo en pre-orden con este contentDescription exacto y bounds no
     * vacíos — misma semántica que el viejo findNodeBoundsByContentDescription.
     */
    fun boundsForContentDescription(target: String): Rect? {
        for (n in nodes) {
            if (n.contentDescription == target && !n.bounds.isEmpty) return n.bounds
        }
        return null
    }

    /** Recicla todos los candidatos salvo la raíz (propiedad del caller). */
    fun recycle() {
        for (n in acceptCandidates) {
            if (n !== rootNode) n.recycle()
        }
    }
}

/**
 * Picap pipeline orchestration: single-pass window scan + decision loop, and
 * records the order in the OrderStateManager / OrderHistoryManager globals.
 *
 * THREADING CONTRACT: [handlePicapEvent] corre en el MAIN thread (solo captura
 * el eventType y voltea los flags de single-flight); el scan/parse/decide
 * pesado corre en [scope] (Dispatchers.Default) con single-flight + trailing
 * edge. Las operaciones de overlay/WindowManager/View se postean a Main con
 * withContext(Dispatchers.Main). El estado de single-flight es
 * MAIN-THREAD-CONFINED (escrito y leído solo en handlePicapEvent y finishScan,
 * ambos en Main), por eso vars simples alcanzan.
 */
@Suppress("DEPRECATION")
class PicapOfferProcessor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val picapWindows: () -> List<AccessibilityNodeInfo>,
    private val overlay: KmOverlay,
    private val policy: PicapAutoAcceptPolicy,
    private val autoclicker: PicapListAutoclicker,
    private val closer: PicapOfferCloser,
) {

    // Almacena el último porcentaje detectado para evitar logs repetitivos de la misma barra
    // (solo se toca dentro del escaneo; el single-flight garantiza un escaneo a la vez).
    private var lastPercentage: Int = -1

    // Almacena la última orden detectada para evitar duplicados por ID
    private var lastOrder: PicapOrder? = null

    // Cooldown para evitar clics repetidos en auto-accept
    private var lastClickTime: Long = 0L
    private val CLICK_COOLDOWN_MS = 2000L

    // --- Single-flight: CONFINED AL MAIN THREAD (ver THREADING CONTRACT) ---
    private var scanInFlight = false
    private var scanPending = false

    // Último eventType visto; el evento no se retiene más allá del callback.
    private var latestEventType: Int = 0

    /**
     * Entry point en MAIN THREAD (onAccessibilityEvent). El objeto evento es
     * válido solo durante el callback, así que se captura su tipo; el pipeline
     * pesado se despacha a [scope] (Default). Si ya hay un escaneo corriendo
     * solo se levanta el flag de trailing edge: no se encola un segundo escaneo.
     */
    fun handlePicapEvent(event: AccessibilityEvent) {
        latestEventType = event.eventType
        if (scanInFlight) {
            scanPending = true
            return
        }
        scanInFlight = true
        launchScan()
    }

    private fun launchScan() {
        val eventType = latestEventType
        scope.launch {
            try {
                runPicapPipeline(eventType)
            } finally {
                withContext(Dispatchers.Main + NonCancellable) { finishScan() }
            }
        }
    }

    /**
     * Corre en MAIN: libera el vuelo o relanza UN escaneo más (trailing edge)
     * si llegaron eventos mientras se escaneaba, para que el estado final de la
     * UI nunca quede desactualizado. Si el scope ya fue cancelado (onDestroy)
     * solo limpia los flags.
     */
    private fun finishScan() {
        if (scanPending && scope.isActive) {
            scanPending = false
            launchScan()
        } else {
            scanInFlight = false
            scanPending = false
        }
    }

    private suspend fun runPicapPipeline(eventType: Int) {
        val picapWindows = picapWindows()
        if (picapWindows.isEmpty()) {
            // Sin ventanas de Picap visibles: el popup de servicio ya no existe
            Log.d(SERVICE_TAG, "PICAP: evento tipo=$eventType sin ventanas visibles; se ignora.")
            hideOverlayOnMain()
            return
        }
        Log.d(SERVICE_TAG, "PICAP: evento tipo=$eventType con ${picapWindows.size} ventana(s).")

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

        // Buscar orden válida en alguna de las ventanas (un solo DFS por ventana)
        for (rootNode in picapWindows) {
            val scan = scanWindow(rootNode)
            val currentPercentage = scan.percentage

            // Lógica de filtrado por porcentaje:
            if (currentPercentage != null) {
                if (currentPercentage < lastPercentage) {
                    val prevPercentage = lastPercentage
                    lastPercentage = currentPercentage
                    // Un popup de oferta real (ID + Aceptar) jamás se descarta por
                    // porcentaje: en modo app el popup vive dentro de la ventana
                    // principal de Picap y su porcentaje puede ser menor al último
                    // visto, lo que silenciaba todo el procesamiento de la oferta.
                    val markers = scan.contentDescriptions
                    val isOfferPopup = markers.any { it.startsWith("ID: ") } &&
                            markers.any { it.contains("Aceptar") }
                    if (isOfferPopup) {
                        Log.i(SERVICE_TAG, "PICAP: porcentaje $currentPercentage < previo $prevPercentage pero es popup de oferta; se procesa igual.")
                    } else {
                        Log.i(SERVICE_TAG, "PICAP: ventana omitida por porcentaje ($currentPercentage < previo $prevPercentage).")
                        scan.recycle()
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
            if (processPicapWindow(rootNode, scan)) {
                scan.recycle()
                picapWindows.forEach { it.recycle() }
                return
            }
            scan.recycle()
            rootNode.recycle()
        }
        Log.d(SERVICE_TAG, "PICAP: sin orden nueva en esta pasada (tipo=$eventType).")
    }

    /**
     * Procesa una ventana de Picap: genera log, extrae orden, intenta el clic
     * de auto-accept y recién entonces actualiza el overlay (el layout pass ya
     * no queda entre el parseo y el clic). Retorna true si se procesó una orden válida.
     */
    private suspend fun processPicapWindow(rootNode: AccessibilityNodeInfo, scan: PicapScanResult): Boolean {
        // 1. GENERAR EL LOG VISUAL DEL ÁRBOL
        if (TREE_DEBUG) {
            AccessibilityTree.logginTree(rootNode)
        }

        // 2. EXTRAER DATOS PARA EL MODELO ORDER (lista plana del único DFS)
        val nodesContent = scan.contentDescriptions
        val order = PicapParser.parseOrder(nodesContent)

        // Si no hay ID, no es una orden válida
        if (order.id.isEmpty()) {
            hideOverlayOnMain()
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

        // --- AUTO-ACCEPT: Evaluar siempre que haya ID, sin importar si es la misma orden ---
        // El intento de clic ocurre ANTES del overlay para no meter un layout pass
        // entre el parseo y el clic.
        var clicked = false
        if (OrderStateManager.isPicapAutoAcceptEnabled.value && policy.shouldAutoAccept(order)) {
            val now = System.currentTimeMillis()
            if (now - lastClickTime >= CLICK_COOLDOWN_MS) {
                Log.d(SERVICE_TAG, "AUTO-ACCEPT: Orden califica para auto-acept. Buscando botón...")
                if (clickAcceptCandidates(scan)) {
                    lastClickTime = System.currentTimeMillis()
                    Log.i(SERVICE_TAG, "AUTO-ACCEPT: Orden auto-aceptada! No se mostrara en UI.")
                    OrderHistoryManager.addOrder(order, context)
                    clicked = true
                }
            } else {
                Log.d(SERVICE_TAG, "AUTO-ACCEPT: En cooldown, esperando...")
            }
        }

        // --- OVERLAY: km totales junto al nodo del precio ---
        // Se actualiza en cada evento (misma orden o nueva) para refrescar posición/texto.
        // SIEMPRE después de la decisión de clic: condiciones no cumplidas, cooldown,
        // auto-accept off, sin botón y clic exitoso (update + hide en el mismo hop de
        // main, para que no entre un frame entre ambos).
        val anchor = scan.boundsForContentDescription(order.ganancia)
        withContext(Dispatchers.Main) {
            overlay.updateKmOverlay(anchor, order)
            if (clicked) overlay.hideKmOverlay()
        }
        if (clicked) return true

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
     * Click phase del escaneo único: recorre [PicapScanResult.acceptCandidates]
     * en orden de recorrido y reproduce la secuencia del findAndClickAcceptButton
     * original por candidato (log → clic directo → clic en el padre).
     * Retorna true si logró hacer clic.
     */
    private fun clickAcceptCandidates(scan: PicapScanResult): Boolean {
        for (node in scan.acceptCandidates) {
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
        return false
    }

    /**
     * UN solo DFS en pre-orden que alimenta todas las entradas de decisión a la
     * vez: lista plana de contentDescriptions (parseo + marcadores de popup de
     * oferta), porcentaje, CD/text/bounds por nodo (ancla del overlay) y
     * candidatos al botón Aceptar en orden de recorrido. Replica el orden de
     * visita de los recorridos originales (flatten, porcentaje, bounds, accept)
     * para conservar la semántica de primer-match.
     */
    private fun scanWindow(rootNode: AccessibilityNodeInfo): PicapScanResult {
        val content = mutableListOf<String>()
        val snapshots = mutableListOf<PicapNodeSnapshot>()
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        var percentage: Int? = null

        fun visit(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false

            val rawCd: String? = node.contentDescription?.toString()
            val cd = rawCd ?: ""
            val text = node.text?.toString() ?: ""

            // Percentage: primer contentDescription que termina en "%" y parsea a Int.
            if (percentage == null && cd.endsWith("%")) {
                val numericValue = cd.replace("%", "").trim().toIntOrNull()
                if (numericValue != null) percentage = numericValue
            }

            // Flatten de contentDescriptions no vacías (input del parseo y de marcadores).
            if (cd.isNotEmpty()) content.add(cd)

            // Snapshot CD+text+bounds de cada nodo (ancla del overlay).
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            snapshots.add(PicapNodeSnapshot(rawCd, text, bounds))

            // Candidato al botón Aceptar: mismo predicado que findAndClickAcceptButton.
            val className = node.className?.toString() ?: ""
            val isAcceptButton = (className.contains("Button") || className.contains("TextView")) &&
                    (text.contains("Aceptar", ignoreCase = true) ||
                            cd.contains("Aceptar", ignoreCase = true))
            if (isAcceptButton) candidates.add(node)

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    val kept = visit(child)
                    if (!kept) child.recycle()
                }
            }
            return isAcceptButton
        }

        visit(rootNode)
        return PicapScanResult(rootNode, content, percentage, snapshots, candidates)
    }

    private suspend fun hideOverlayOnMain() {
        withContext(Dispatchers.Main) { overlay.hideKmOverlay() }
    }
}
