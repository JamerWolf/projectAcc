package com.example.projectacc.picap

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.projectacc.SERVICE_TAG
import com.example.projectacc.a11y.AccessibilityTree
import com.example.projectacc.parser.PicapParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Espera antes de cerrar una oferta no-CV: da tiempo al popup de renderizar
// el nodo de servicio "Cruz Verde ..." antes de decidir que no lo es.
const val NON_CV_CLOSE_DELAY_MS = 200L

/**
 * Resultado de un intento de cierre de la oferta no-CV.
 * CLOSED y CV_RENDERED son terminales; el resto reintenta.
 */
internal enum class NonCvCloseOutcome { CLOSED, CV_RENDERED, NO_POPUP, NO_OFFER, CLICK_FAILED }

/**
 * Close pipeline for non-Cruz-Verde offers: schedules the attempt on the
 * service scope, re-reads the Picap windows and verifies the popup is gone.
 *
 * @param scope the service scope (serviceScope); never create another scope.
 * @param picapWindows provider of the Picap window roots.
 * @param hideKmOverlayOnMain hides the km overlay by posting it to the main
 * thread (WindowManager lives on the main thread).
 */
@Suppress("DEPRECATION")
class PicapOfferCloser(
    private val scope: CoroutineScope,
    private val picapWindows: () -> List<AccessibilityNodeInfo>,
    private val hideKmOverlayOnMain: () -> Unit,
) {

    // ID de la orden no-CV con cierre agendado (evita duplicar el timer de 200 ms).
    // Volatile: se escribe en el evento y se lee/libera desde la coroutine.
    @Volatile internal var pendingCloseId: String = ""

    /**
     * Agenda el cierre de una oferta no-Cruz-Verde con el scope del servicio
     * (coroutines), NO con Handler.postDelayed: en el dispositivo el timer del
     * main looper nunca llegó a ejecutarse (pendingCloseId quedaba seteado y los
     * eventos repetían "ya tiene timer activo" para siempre). Hasta 3 intentos:
     * el primero a NON_CV_CLOSE_DELAY_MS, los reintentos con espera creciente.
     */
    fun scheduleNonCvClose(orderId: String) {
        scope.launch {
            val maxAttempts = 3
            var attempt = 0
            var outcome: NonCvCloseOutcome
            do {
                attempt++
                delay(if (attempt == 1) NON_CV_CLOSE_DELAY_MS else NON_CV_CLOSE_DELAY_MS * attempt)
                Log.i(SERVICE_TAG, "NO-CV: timer ejecutado para #$orderId (intento $attempt/$maxAttempts, pending=$pendingCloseId).")
                outcome = attemptNonCvClose(orderId, attempt, maxAttempts)
            } while (attempt < maxAttempts &&
                outcome != NonCvCloseOutcome.CLOSED &&
                outcome != NonCvCloseOutcome.CV_RENDERED)

            if (outcome != NonCvCloseOutcome.CLOSED && outcome != NonCvCloseOutcome.CV_RENDERED) {
                Log.i(SERVICE_TAG, "NO-CV: orden #$orderId sin cierre tras $maxAttempts intentos (ultimo=$outcome).")
            }
            if (pendingCloseId == orderId) {
                Log.d(SERVICE_TAG, "NO-CV: timer finalizado (#$orderId); pendingCloseId='$pendingCloseId' -> ''.")
                pendingCloseId = ""
            }
        }
    }

    /**
     * Relee las ventanas Picap y aplica la regla de producto: popup de oferta sin
     * nodo "cruz verde" → clic en la X, aunque el id leído difiera del agendado
     * (parseOrder se queda con el último ID del árbol y en modo app la ventana
     * puede reordenarse entre la detección y el relectura). Se ejecuta en
     * Dispatchers.Default; hideKmOverlay se postea al main porque usa WindowManager.
     * performAction=true NO garantiza que se clickeó la X correcta: tras el clic
     * se espera 300 ms y se verifica que el popup haya desaparecido antes de dar
     * la orden por cerrada (no se memorizan ids: si la misma oferta reaparece,
     * la rama NO-CV la vuelve a agendar y a cerrar).
     */
    private suspend fun attemptNonCvClose(orderId: String, attempt: Int, maxAttempts: Int): NonCvCloseOutcome {
        val picapWindows = try {
            picapWindows()
        } catch (e: Exception) {
            Log.d(SERVICE_TAG, "NO-CV: lectura de ventanas fallo en intento $attempt (#$orderId): ${e.message}")
            return NonCvCloseOutcome.NO_POPUP
        }
        if (picapWindows.isEmpty()) {
            Log.d(SERVICE_TAG, "NO-CV: sin ventanas Picap en intento $attempt (#$orderId).")
            return NonCvCloseOutcome.NO_POPUP
        }
        for (rootNode in picapWindows) {
            val nodesContent = mutableListOf<String>()
            AccessibilityTree.flattenContentDescriptions(rootNode, nodesContent)
            val order = PicapParser.parseOrder(nodesContent)
            val isOfferPopup = nodesContent.any { it.contains("Aceptar", ignoreCase = true) }
            if (!isOfferPopup) {
                Log.d(SERVICE_TAG, "NO-CV: intento $attempt sin popup de oferta (id='${order.id}', nodos=${nodesContent.size}; esperado #$orderId).")
                continue
            }
            if (order.id != orderId) {
                Log.d(SERVICE_TAG, "NO-CV: intento $attempt con id distinto '${order.id}' (esperado #$orderId); se decide por nodo de servicio.")
            }
            if (order.servicio.isNotEmpty()) {
                Log.i(SERVICE_TAG, "NO-CV: orden #$orderId ya mostro el nodo Cruz Verde (intento $attempt, id leido '${order.id}'); se omite el cierre.")
                picapWindows.forEach { it.recycle() }
                return NonCvCloseOutcome.CV_RENDERED
            }
            val clicked = findAndClickCloseButton(rootNode)
            Log.i(SERVICE_TAG, "NO-CV: orden #$orderId sin nodo Cruz Verde (intento $attempt/$maxAttempts, id leido '${order.id}'); clic enviado (click=$clicked); verificando en 300 ms.")
            picapWindows.forEach { it.recycle() }
            hideKmOverlayOnMain()
            if (!clicked) return NonCvCloseOutcome.CLICK_FAILED
            delay(300)
            if (picapOfferStillVisible()) {
                Log.i(SERVICE_TAG, "NO-CV: click=true pero el popup de #$orderId sigue visible tras 300 ms; se reintenta.")
                return NonCvCloseOutcome.CLICK_FAILED
            }
            Log.i(SERVICE_TAG, "NO-CV: orden #$orderId cerrada y verificada (popup desaparecio); no registrada.")
            return NonCvCloseOutcome.CLOSED
        }
        val windowCount = picapWindows.size
        picapWindows.forEach { it.recycle() }
        Log.d(SERVICE_TAG, "NO-CV: intento $attempt sin ventana de oferta entre $windowCount ventana(s) (#$orderId).")
        return NonCvCloseOutcome.NO_OFFER
    }

    /**
     * Verifica tras un clic si alguna ventana Picap todavía muestra el popup de
     * oferta (nodos "ID: " y "Aceptar"). Se llama desde la coroutine de cierre.
     */
    private fun picapOfferStillVisible(): Boolean {
        val wins = try {
            picapWindows()
        } catch (e: Exception) {
            return false
        }
        var visible = false
        for (w in wins) {
            val content = mutableListOf<String>()
            AccessibilityTree.flattenContentDescriptions(w, content)
            if (content.any { it.startsWith("ID: ") } && content.any { it.contains("Aceptar") }) {
                visible = true
            }
        }
        wins.forEach { it.recycle() }
        return visible
    }

    /**
     * Localiza el contenedor del popup: el ancestro más externo de "Aceptar" con
     * bounds estrictamente menores a la ventana (inset, se corta al llegar al
     * layout a pantalla completa) que además contiene un nodo "ID: " en su
     * subárbol. Devuelve null si no hay; el caller entonces usa la ventana
     * completa. El nodo devuelto es propiedad del caller.
     */
    private fun findPopupContainer(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val aceptar = AccessibilityTree.findFirstNodeMatching(rootNode) { it.contains("Aceptar") } ?: return null
        val rootRect = Rect().also { rootNode.getBoundsInScreen(it) }
        val chain = mutableListOf(aceptar)
        var current: AccessibilityNodeInfo = aceptar
        var levels = 0
        while (levels < 8) {
            val parent = current.parent ?: break
            val pr = Rect().also { parent.getBoundsInScreen(it) }
            val fullSize = pr.width() >= rootRect.width() * 0.98f && pr.height() >= rootRect.height() * 0.98f
            if (fullSize) {
                parent.recycle()
                break
            }
            chain.add(parent)
            current = parent
            levels++
        }
        var best: AccessibilityNodeInfo? = null
        for (node in chain) {
            val content = mutableListOf<String>()
            AccessibilityTree.flattenContentDescriptions(node, content)
            if (content.any { it.startsWith("ID: ") }) best = node
        }
        chain.forEach { if (it !== best) it.recycle() }
        return best
    }

    /**
     * Busca el botón de cerrar (X) del popup y hace clic para descartar la oferta.
     * En modo app el árbol de Picap es plano (Compose): el X, el chevron superior
     * y "Aceptar" son hermanos, no hay contenedor de tarjeta, así que el scope
     * equivale a la pantalla completa y el X cae ~62% hacia abajo. Una banda
     * superior al 40% metía al chevron centrado y EXCLUIA al X (click en la
     * flecha sin cerrar). Por eso no hay banda: se evalúa todo el scope sobre
     * ImageView sin CD ni texto y se prioriza (1) más clickeable, (2) el más a
     * la derecha (la X vive en la esquina superior derecha; el chevron va al
     * centro), (3) empate → más arriba. Retorna true si performAction devolvió
     * true; el caller verifica si el popup realmente desapareció.
     */
    private fun findAndClickCloseButton(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false
        val rootRect = Rect().also { rootNode.getBoundsInScreen(it) }

        val scope = findPopupContainer(rootNode)
        val scopeRect = Rect().also { (scope ?: rootNode).getBoundsInScreen(it) }
        val scopeLabel = if (scope != null) "popup" else "ventana"

        // Candidatos geométricos
        val searchRoot: AccessibilityNodeInfo = scope ?: rootNode
        val all = mutableListOf<AccessibilityNodeInfo>()
        AccessibilityTree.collectImageViewCandidates(searchRoot, all)
        if (all.isEmpty()) {
            Log.d(SERVICE_TAG, "CLOSE: sin candidatos ImageView sin CD ni texto en $scopeLabel (scope=$scopeRect); click omitido.")
            if (searchRoot !== rootNode) searchRoot.recycle()
            return false
        }

        val desc = StringBuilder()
        var best: AccessibilityNodeInfo? = null
        var bestRank = -1
        var bestRight = Int.MIN_VALUE
        var bestTop = Int.MAX_VALUE
        for (c in all) {
            val r = Rect().also { c.getBoundsInScreen(it) }
            val rank = if (c.isClickable) 2 else {
                val p = c.parent
                val parentClickable = p?.isClickable == true
                p?.recycle()
                if (parentClickable) 1 else 0
            }
            desc.append("[top=").append(r.top).append(",left=").append(r.left)
                .append(",right=").append(r.right).append(",bottom=").append(r.bottom)
                .append(",click=").append(rank)
                .append(",id=").append(c.viewIdResourceName?.substringAfterLast('/') ?: "-")
                .append("] ")
            val better = best == null || rank > bestRank ||
                (rank == bestRank && (r.right > bestRight || (r.right == bestRight && r.top < bestTop)))
            if (better) {
                best = c
                bestRank = rank
                bestRight = r.right
                bestTop = r.top
            }
        }
        Log.d(SERVICE_TAG, "CLOSE: candidatos($scopeLabel, scope=$scopeRect, ventana=$rootRect): $desc")

        var clicked = false
        val target = best
        if (target != null) {
            val tr = Rect().also { target.getBoundsInScreen(it) }
            Log.d(SERVICE_TAG, "CLOSE: elegido[$scopeLabel] top=${tr.top} left=${tr.left} right=${tr.right} bottom=${tr.bottom} click=$bestRank className=${target.className}")
            clicked = AccessibilityTree.clickNode(target)
            Log.d(SERVICE_TAG, "CLOSE: performAction=$clicked")
        }

        all.forEach { if (it !== rootNode && it !== scope) it.recycle() }
        if (scope != null && scope !== rootNode) scope.recycle()
        return clicked
    }
}
