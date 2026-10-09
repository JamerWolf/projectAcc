package com.example.projectacc

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.projectacc.a11y.AccessibilityTree
import com.example.projectacc.floating.FloatingPopupManager
import com.example.projectacc.picap.KmOverlay
import com.example.projectacc.picap.PicapAutoAcceptPolicy
import com.example.projectacc.picap.PicapEventThrottle
import com.example.projectacc.picap.PicapListAutoclicker
import com.example.projectacc.picap.PicapOfferCloser
import com.example.projectacc.picap.PicapOfferProcessor
import com.example.projectacc.whatsapp.WhatsAppResponder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Log tag shared with every collaborator so logcat markers (CLOSE:, NO-CV:,
 * PICAP-THROTTLE:, WHATSAPP:, ...) stay byte-identical.
 */
const val SERVICE_TAG = "MyAccessibilityService"

private const val EVENT_DEBUG = false

/**
 * Thin AccessibilityService shell: lifecycle, per-package event routing and
 * the public facade. Behavior lives in the collaborators: [PicapEventThrottle],
 * [PicapOfferProcessor] (+ closer/policy/autoclicker/overlay),
 * [WhatsAppResponder] and [AccessibilityTree].
 */
@Suppress("DEPRECATION")
class MyAccessibilityService : AccessibilityService() {
    private val TAG = SERVICE_TAG

    companion object {
        var instance: MyAccessibilityService? = null
            private set

        const val PACKAGE_PICAP = "co.picap.passenger"
        const val PACKAGE_WHATSAPP = "com.whatsapp"
        const val PACKAGE_WHATSAPP_BIZ = "com.whatsapp.w4b"
    }

    private var floatingPopup: FloatingPopupManager? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val picapThrottle = PicapEventThrottle()
    private val kmOverlay = KmOverlay(this)
    private val picapWindowsProvider = { AccessibilityTree.findAllWindows(windows, PACKAGE_PICAP) }
    private val offerCloser = PicapOfferCloser(
        serviceScope,
        picapWindowsProvider,
        { android.os.Handler(android.os.Looper.getMainLooper()).post { kmOverlay.hideKmOverlay() } },
    )
    private val autoAcceptPolicy = PicapAutoAcceptPolicy(this)
    private val listAutoclicker = PicapListAutoclicker()
    private val offerProcessor = PicapOfferProcessor(
        this,
        picapWindowsProvider,
        kmOverlay,
        autoAcceptPolicy,
        listAutoclicker,
        offerCloser,
    )
    private val whatsappResponder = WhatsAppResponder(
        serviceScope,
        this,
        { rootInActiveWindow },
        { floatingPopup },
    )

    /**
     * Paste only - pega texto SIN enviar. Para que el usuario revise y envíe manual.
     */
    fun pasteOnly(text: String) {
        whatsappResponder.pasteOnly(text)
    }

    /**
     * Copies text to clipboard, then performs paste + send on the current WhatsApp input.
     * Uses retry mechanism for cases where multiple messages come in simultaneously.
     */
    fun pasteAndSend(text: String, notificationTimestamp: Long = 0L) {
        whatsappResponder.pasteAndSend(text, notificationTimestamp)
    }

    /**
     * Checks if there's a text input field in the current window.
     */
    fun hasTextInput(): Boolean {
        return whatsappResponder.hasTextInput()
    }

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
        kmOverlay.hideKmOverlay()
        serviceScope.cancel()
        floatingPopup?.dismiss()
        floatingPopup = null
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // === THROTTLE PICAP: en ráfaga, procesa como máximo 1 evento/seg ===
        // El conteo corre siempre (incluso para eventos descartados) para poder
        // detectar cuándo la ráfaga baja y reanudar el proceso normal.
        if (event?.packageName?.toString() == PACKAGE_PICAP && !picapThrottle.picapEventAllowed()) {
            return
        }

        if (EVENT_DEBUG) {
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
        }

        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        // === PICAP HANDLING ===
        if (packageName == PACKAGE_PICAP) {
            offerProcessor.handlePicapEvent(event)
            return
        }

        // === WHATSAPP HANDLING ===
        if (packageName == PACKAGE_WHATSAPP || packageName == PACKAGE_WHATSAPP_BIZ) {
            whatsappResponder.handleWhatsAppEvent(event)
            return
        }
    }

    override fun onInterrupt() {
        // Método requerido por la interfaz de AccessibilityService
    }
}
