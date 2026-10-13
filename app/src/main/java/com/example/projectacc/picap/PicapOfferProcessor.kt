package com.example.projectacc.picap

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TREE_DEBUG = true

/**
 * El botón del popup renderiza EXACTAMENTE la etiqueta "Aceptar" (trim +
 * ignore case) en text o contentDescription. El match por subcadena era
 * letal: el banner de filtro de Picap "No cumple tus filtros de
 * autoaceptar" contiene "aceptar" y va ANTES del botón en pre-orden, así
 * que los bounds del tap y ACTION_CLICK apuntaban al banner y el gesto
 * caía sobre texto plano (regresión v1.0.2, orden #db527: 19 taps en
 * (518,1336) sin cerrar el popup; el botón real vive en ~(540,2157)).
 */
internal fun isAcceptButtonLabel(text: String, contentDescription: String?): Boolean =
    text.trim().equals("Aceptar", ignoreCase = true) ||
            contentDescription?.trim()?.equals("Aceptar", ignoreCase = true) == true

/** Contiene "aceptar" en cualquier parte. SOLO para diagnóstico de campo. */
internal fun isAcceptLikeLabel(text: String, contentDescription: String?): Boolean =
    text.contains("Aceptar", ignoreCase = true) ||
            contentDescription?.contains("Aceptar", ignoreCase = true) == true

/**
 * Snapshot de un nodo recolectado por el escaneo único: contentDescription
 * crudo (null preservado), text, bounds y flags en pantalla. Es la materia
 * prima que KmOverlay antes buscaba con su propio recorrido del árbol.
 */
private class PicapNodeSnapshot(
    val className: String,
    val contentDescription: String?,
    val text: String,
    val bounds: Rect,
    val clickable: Boolean,
    val enabled: Boolean,
    val visibleToUser: Boolean,
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

    /**
     * Primer nodo (pre-orden) con label EXACTO "Aceptar" y bounds no vacíos:
     * materia prima del tap de gesto y de ACTION_CLICK.
     */
    fun chosenAcceptSnapshot(): PicapNodeSnapshot? =
        nodes.firstOrNull {
            isAcceptButtonLabel(it.text, it.contentDescription) && !it.bounds.isEmpty
        }

    /** Bounds del botón Aceptar elegido para el tap, o null si no existe. */
    fun boundsForAcceptLabel(): Rect? = chosenAcceptSnapshot()?.bounds

    /**
     * Diagnóstico de campo (TREE_DEBUG): lista TODOS los nodos cuyo text/CD
     * contiene "aceptar" — incluyendo descartados como el banner "No cumple
     * tus filtros de autoaceptar" — con sus detalles, marcando el elegido
     * para el tap. Si un descartado tiene bounds plausibles y el ELEGIDO no,
     * el matcher es el sospechoso.
     */
    fun describeAcceptMatches(): String {
        val loose = nodes.filter { isAcceptLikeLabel(it.text, it.contentDescription) }
        if (loose.isEmpty()) return "ACCEPT-BTN: sin nodos que contengan 'aceptar'."
        val chosen = chosenAcceptSnapshot()
        return loose.joinToString(separator = "\n") { n ->
            val tag = if (n === chosen) "ELEGIDO " else "descartado"
            "ACCEPT-BTN: $tag class=${n.className} text=\"${n.text}\" cd=\"${n.contentDescription}\" " +
                    "bounds=${n.bounds} clickable=${n.clickable} enabled=${n.enabled} visibleToUser=${n.visibleToUser}"
        }
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

    // Re-clic para la MISMA orden pendiente: el éxito real es el CIERRE del
    // popup, no el return de performAction (probado mentiroso en el botón
    // 2026-10). Se reintenta con la cadencia de los scans.
    private val CLICK_RETRY_MIN_INTERVAL_MS = 500L
    // Ventana máxima con el popup visible re-clickeando antes de rendirse
    // (el usuario puede querer decidir a mano).
    private val PENDING_ACCEPT_TIMEOUT_MS = 15_000L

    // Máx. de niveles de padres a subir probando ACTION_CLICK del nodo "Aceptar"
    // (el botón animado suele ser un contenedor; en Compose la cadena puede ser
    // larga, por eso el tope generoso).
    private val MAX_PARENT_HOPS = 12

    // Gesture de respaldo: tap físico en el centro del nodo "Aceptar" cuando
    // ACTION_CLICK no llega a la vista real. Requiere canPerformGestures=true.
    private val GESTURE_TAP_DURATION_MS = 100L
    private val GESTURE_TIMEOUT_MS = 1500L

    // Fallback de polling: la animación del botón ya no genera eventos de
    // accesibilidad (la barra de porcentaje desapareció en el update de Picap),
    // así que el pipeline se auto-dispara cada 750 ms mientras haya ventanas
    // de Picap visibles. Los ticks se marcan con EVENT_TYPE_POLL para silenciar
    // los logs rutinarios.
    private val PICAP_POLL_INTERVAL_MS = 750L
    private val EVENT_TYPE_POLL = -1

    // --- Single-flight: CONFINED AL MAIN THREAD (ver THREADING CONTRACT) ---
    private var scanInFlight = false
    private var scanPending = false

    // Último eventType visto; el evento no se retiene más allá del callback.
    private var latestEventType: Int = 0

    // Estado de auto-accept pendiente de confirmación (solo pipeline, serializado
    // por single-flight en Default): el click se despacha pero la orden se
    // registra recién cuando el popup de ESA orden desaparece.
    private var pendingAcceptOrderId: String? = null
    private var pendingAcceptOrder: PicapOrder? = null
    private var pendingAcceptAttempts: Int = 0
    private var pendingAcceptStart: Long = 0L

    // Si dispatchGesture falla en este dispositivo (flag canPerformGestures
    // ausente), se cae a performAction como red de compatibilidad.
    private var gestureUnavailable = false

    /**
     * Registra la orden pendiente como auto-aceptada: su popup desapareció
     * después de los intentos de clic (la única confirmación disponible).
     */
    private suspend fun confirmPendingAccept(reason: String) {
        val order = pendingAcceptOrder
        if (order == null) {
            pendingAcceptOrderId = null
            return
        }
        pendingAcceptOrderId = null
        pendingAcceptOrder = null
        pendingAcceptAttempts = 0
        Log.i(
            SERVICE_TAG,
            "AUTO-ACCEPT: Popup de orden #${order.id} desapareció ($reason); registrada como auto-aceptada."
        )
        OrderHistoryManager.addOrder(order, context)
    }

    /** Descarta el pendiente sin registrar (timeout o popup reemplazado). */
    private suspend fun abandonPendingAccept(reason: String) {
        if (pendingAcceptOrderId != null) {
            Log.i(SERVICE_TAG, "AUTO-ACCEPT: Pendiente #${pendingAcceptOrderId} descartada ($reason).")
        }
        pendingAcceptOrderId = null
        pendingAcceptOrder = null
        pendingAcceptAttempts = 0
    }

    /**
     * Entry point en MAIN THREAD (onAccessibilityEvent). El objeto evento es
     * válido solo durante el callback, así que se captura su tipo; el pipeline
     * pesado se despacha a [scope] (Default). Si ya hay un escaneo corriendo
     * solo se levanta el flag de trailing edge: no se encola un segundo escaneo.
     */
    fun handlePicapEvent(event: AccessibilityEvent) {
        requestScan(event.eventType)
    }

    /**
     * Inicia un escaneo con single-flight; [eventType] solo alimenta los logs
     * (EVENT_TYPE_POLL marca los ticks del polling de respaldo). Debe invocarse
     * en Main para respetar el confinamiento de los flags de vuelo.
     */
    private fun requestScan(eventType: Int) {
        latestEventType = eventType
        if (scanInFlight) {
            scanPending = true
            return
        }
        scanInFlight = true
        launchScan()
    }

    init {
        // Polling de respaldo (ver PICAP_POLL_INTERVAL_MS). Corre en [scope];
        // primero pregunta barato si hay ventanas de Picap y solo entonces
        // pide un escaneo, posteados a Main para respetar el threading contract.
        scope.launch {
            while (isActive) {
                delay(PICAP_POLL_INTERVAL_MS)
                val windows = picapWindows()
                val hasWindows = windows.isNotEmpty()
                windows.forEach { it.recycle() }
                if (hasWindows) {
                    withContext(Dispatchers.Main) { requestScan(EVENT_TYPE_POLL) }
                }
            }
        }
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
        val isPoll = eventType == EVENT_TYPE_POLL
        val picapWindows = picapWindows()
        if (picapWindows.isEmpty()) {
            // Sin ventanas de Picap visibles: el popup de servicio ya no existe
            if (!isPoll) {
                Log.d(SERVICE_TAG, "PICAP: evento tipo=$eventType sin ventanas visibles; se ignora.")
            }
            confirmPendingAccept("sin ventanas de Picap")
            hideOverlayOnMain()
            return
        }

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
    }

    /**
     * Procesa una ventana de Picap: genera log, extrae orden, intenta el clic
     * de auto-accept y recién entonces actualiza el overlay (el layout pass ya
     * no queda entre el parseo y el clic). Retorna true si se procesó una orden válida.
     */
    private suspend fun processPicapWindow(rootNode: AccessibilityNodeInfo, scan: PicapScanResult): Boolean {
        // 1. EXTRAER DATOS PARA EL MODELO ORDER (lista plana del único DFS)
        val nodesContent = scan.contentDescriptions
        val order = PicapParser.parseOrder(nodesContent)

        // Si no hay ID, no es una orden válida
        if (order.id.isEmpty()) {
            confirmPendingAccept("popup sin ID de orden")
            hideOverlayOnMain()
            return false
        }

        // 2. LOG VISUAL DEL ÁRBOL: solo cuando hay una orden real (con ID),
        //    es decir cuando "sale" una orden en Picap. Antes se loggeaba en
        //    cada ventana procesada y se desactivó por costo (commit 0f6abf8).
        if (TREE_DEBUG) {
            AccessibilityTree.logginTree(rootNode)
        }

        // --- NO CRUZ VERDE: sin el nodo de servicio "Cruz Verde ..." (servicio vacío)
        // y con estructura de popup de oferta → agendar el cierre en 200 ms (tiempo de
        // render) y NO registrar la orden en la tarjeta ni en el historial.
        // Regla de producto: si no es Cruz Verde SIEMPRE se cierra; no se memorizan
        // ids de servicios ya cerrados (una oferta que reaparezca se vuelve a cerrar).
        // El CD del botón es exactamente "Aceptar"; match exacto para no confundir
        // el banner de filtro "No cumple tus filtros de autoaceptar" con un popup.
        if (order.servicio.isEmpty() && nodesContent.any { isAcceptButtonLabel(it, null) }) {
            if (order.id == closer.pendingCloseId) {
                Log.d(SERVICE_TAG, "NO-CV: orden #${order.id} ya tiene timer activo (pending=${closer.pendingCloseId}); no se re-agenda.")
            } else {
                closer.pendingCloseId = order.id
                Log.i(SERVICE_TAG, "NO-CV: orden #${order.id} sin nodo Cruz Verde; cierre agendado en ${NON_CV_CLOSE_DELAY_MS} ms (serviceScope).")
                closer.scheduleNonCvClose(order.id)
            }
            return false
        }

        // --- AUTO-ACCEPT ---
        // El click despachado NO confirma la aceptación: performAction puede
        // retornar true aunque la vista real del botón ignora el clic (botón
        // animado 2026-10 / delegate anti-automatización). Se re-intenta mientras
        // el popup de ESTA orden siga visible (cadencia de los scans) y se
        // registra recién cuando el popup desaparece (confirmPendingAccept).
        if (OrderStateManager.isPicapAutoAcceptEnabled.value && policy.shouldAutoAccept(order)) {
            val now = System.currentTimeMillis()
            if (pendingAcceptOrderId != null && order.id != pendingAcceptOrderId) {
                // El popup pendiente fue reemplazado por otra oferta: no se registra.
                abandonPendingAccept("reemplazada por #${order.id}")
            }
            if (order.id == pendingAcceptOrderId && now - pendingAcceptStart > PENDING_ACCEPT_TIMEOUT_MS) {
                abandonPendingAccept("timeout de $PENDING_ACCEPT_TIMEOUT_MS ms con popup visible")
            }
            val isPending = order.id == pendingAcceptOrderId
            val interval = if (isPending) CLICK_RETRY_MIN_INTERVAL_MS else CLICK_COOLDOWN_MS
            if (now - lastClickTime >= interval) {
                Log.d(
                    SERVICE_TAG,
                    "AUTO-ACCEPT: Orden #${order.id} califica (intento ${pendingAcceptAttempts + 1}, pendiente=$isPending)."
                )
                // Gesto físico DIRECTO como primer recurso: performAction es un
                // probado mentiroso en el botón 2026-10 (retorna true sin clic
                // real) y el tap físico funciona en cualquier versión del popup.
                // El bounds del botón viene del scan fresco de este tick.
                val dispatched = if (!gestureUnavailable) {
                    val gestureOk = clickAcceptByGesture(scan)
                    if (!gestureOk) {
                        // Gesto no disponible en el dispositivo: performAction de respaldo.
                        gestureUnavailable = true
                        clickAcceptCandidates(scan)
                    } else {
                        true
                    }
                } else {
                    clickAcceptCandidates(scan)
                }
                if (dispatched) {
                    lastClickTime = now
                    if (!isPending) {
                        pendingAcceptStart = now
                        pendingAcceptOrder = order
                    }
                    pendingAcceptOrderId = order.id
                    pendingAcceptAttempts++
                    Log.i(
                        SERVICE_TAG,
                        "AUTO-ACCEPT: Click despachado en #${order.id} (intento ${pendingAcceptAttempts}); esperando cierre del popup para confirmar."
                    )
                }
            } else {
                Log.d(SERVICE_TAG, "AUTO-ACCEPT: En cooldown, esperando...")
            }
        }

        // --- OVERLAY: km totales junto al nodo del precio ---
        // Se actualiza en cada evento (misma orden o nueva) para refrescar posición/texto.
        // SIEMPRE después de la decisión de clic (condiciones no cumplidas,
        // cooldown o click despachado).
        val anchor = scan.boundsForContentDescription(order.ganancia)
        withContext(Dispatchers.Main) {
            overlay.updateKmOverlay(anchor, order)
        }

        // Orden con click ya despachado: se reintenta en cada scan y se confirma
        // al cerrarse el popup; todavía no se registra en UI ni en historial.
        if (order.id == pendingAcceptOrderId) return true

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
     * en orden de recorrido y por cada candidato sube la cadena de padres
     * (máx. [MAX_PARENT_HOPS]) probando performAction en el nodo y en cada
     * ancestro. El gate de isClickable se eliminó: performAction en un nodo sin
     * la acción retorna false y sigue, así que el intento directo cubre nodos
     * con la acción expuesta aunque isClickable diga lo contrario.
     * Retorna true si logró hacer clic.
     */
    private fun clickAcceptCandidates(scan: PicapScanResult): Boolean {
        for (node in scan.acceptCandidates) {
            val actions = node.actionList.joinToString(";") { "id=${it.id} label=${it.label}" }
            Log.d(
                SERVICE_TAG,
                "AUTO-ACCEPT: Candidato 'Aceptar' (class=${node.className}, clickable=${node.isClickable}, enabled=${node.isEnabled}, actions=[$actions]). Intentando clic..."
            )
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(SERVICE_TAG, "AUTO-ACCEPT: Clic exitoso en botón Aceptar!")
                return true
            }
            var parent = node.parent
            var hops = 0
            while (parent != null && hops < MAX_PARENT_HOPS) {
                if (parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    Log.d(SERVICE_TAG, "AUTO-ACCEPT: Clic exitoso en ancestro (hops=$hops, class=${parent.className}).")
                    parent.recycle()
                    return true
                }
                val next = parent.parent
                parent.recycle()
                parent = next
                hops++
            }
            parent?.recycle()
        }
        Log.w(
            SERVICE_TAG,
            "AUTO-ACCEPT: ${scan.acceptCandidates.size} candidato(s) 'Aceptar' sin clic efectivo vía ACTION_CLICK; probando gesto..."
        )
        return false
    }

    /**
     * Tap de respaldo vía dispatchGesture: si ACTION_CLICK no llega a la vista
     * real (botón animado custom que no expone la acción de accesibilidad), se
     * despacha un toque físico en el centro del nodo "Aceptar" usando los bounds
     * del scan. Requiere android:canPerformGestures="true" en el service config.
     * Con timeout: si el sistema no confirma el gesto, se libera el vuelo.
     */
    private suspend fun clickAcceptByGesture(scan: PicapScanResult): Boolean {
        val service = context as? AccessibilityService ?: return false
        val bounds = scan.boundsForAcceptLabel() ?: run {
            Log.w(SERVICE_TAG, "AUTO-ACCEPT: Gesto abortado; ningún nodo con 'Aceptar' y bounds válidos.")
            return false
        }
        val cx = bounds.centerX().toFloat()
        val cy = bounds.centerY().toFloat()
        if (TREE_DEBUG) {
            // Diagnóstico de campo: qué nodos casaron "aceptar" y cuál eligió
            // el tap (comparar bounds del ELEGIDO con la posición visual real).
            Log.i(SERVICE_TAG, scan.describeAcceptMatches())
            Log.i(SERVICE_TAG, "ACCEPT-BTN: tap planeado en ($cx, $cy) sobre bounds $bounds.")
        }
        Log.d(SERVICE_TAG, "AUTO-ACCEPT: Despachando gesto de respaldo en ($cx, $cy)...")
        val result = withTimeoutOrNull(GESTURE_TIMEOUT_MS) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val path = Path().apply { moveTo(cx, cy) }
                    val gesture = GestureDescription.Builder()
                        .addStroke(GestureDescription.StrokeDescription(path, 0, GESTURE_TAP_DURATION_MS))
                        .build()
                    val dispatched = try {
                        service.dispatchGesture(
                            gesture,
                            object : AccessibilityService.GestureResultCallback() {
                                override fun onCompleted(gestureDescription: GestureDescription) {
                                    if (cont.isActive) cont.resume(true)
                                }

                                override fun onCancelled(gestureDescription: GestureDescription) {
                                    Log.w(SERVICE_TAG, "AUTO-ACCEPT: Gesto cancelado por el sistema.")
                                    if (cont.isActive) cont.resume(false)
                                }
                            },
                            null
                        )
                    } catch (e: Exception) {
                        Log.w(SERVICE_TAG, "AUTO-ACCEPT: dispatchGesture falló: ${e.message}")
                        false
                    }
                    if (!dispatched && cont.isActive) {
                        cont.resume(false)
                    }
                }
            }
        } ?: false
        Log.d(SERVICE_TAG, "AUTO-ACCEPT: Gesto de respaldo resultado=$result.")
        return result
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

            // Snapshot CD+text+bounds+clase de cada nodo (ancla del overlay).
            val className = node.className?.toString() ?: ""
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            snapshots.add(
                PicapNodeSnapshot(
                    className = className,
                    contentDescription = rawCd,
                    text = text,
                    bounds = bounds,
                    clickable = node.isClickable,
                    enabled = node.isEnabled,
                    visibleToUser = node.isVisibleToUser,
                )
            )

            // Candidato al botón Aceptar: label EXACTO "Aceptar" (text o CD).
            // El popup 2026-10 renderiza el botón en un contenedor custom (el
            // relleno animado vive en otra vista) y la clase ya no es
            // confiable, pero la etiqueta sí: el match por subcadena casaba
            // también el banner de filtro "No cumple tus filtros de
            // autoaceptar" (va antes en pre-orden) y ni ACTION_CLICK ni el
            // tap de gesto llegaban al botón real (#db527).
            val isAcceptButton = isAcceptButtonLabel(text, rawCd)
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
