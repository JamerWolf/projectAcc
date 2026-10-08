package com.example.projectacc.picap

import android.os.SystemClock
import android.util.Log
import com.example.projectacc.SERVICE_TAG

/**
 * Flood detection + 1 event/sec throttle for Picap events. Owns the throttle
 * state and constants; invoked from the main thread (onAccessibilityEvent) for
 * EVERY Picap event.
 */
class PicapEventThrottle {

    // --- Throttle adaptativo de eventos Picap (contra ráfagas) ---
    // Si en una ventana de 1 s llegan más de PICAP_FLOOD_HIGH eventos, se limita el
    // procesamiento a 1 evento por segundo; se vuelve al proceso normal cuando un
    // segundo completo baja a PICAP_FLOOD_LOW o menos (histéresis anti-flapping).
    private val PICAP_FLOOD_WINDOW_MS = 1000L
    private val PICAP_FLOOD_HIGH = 8
    private val PICAP_FLOOD_LOW = 4
    private val PICAP_THROTTLE_MIN_INTERVAL_MS = 1000L

    // Estado del throttle de eventos Picap (solo hilo principal: onAccessibilityEvent).
    private var picapFloodWindowStartMs = 0L
    private var picapFloodCount = 0
    private var picapThrottleActive = false
    private var picapEventsDropped = 0
    private var picapLastProcessedMs = 0L

    /**
     * Detección de ráfaga + throttle de 1 evento/seg para eventos de Picap.
     * Devuelve true si el evento debe procesarse; false si se descarta por el límite.
     * Se invoca para CADA evento de Picap (también los descartados) porque el conteo
     * de la ventana de 1 s es lo que decide entrar/salir del modo limitado.
     * El pipeline es seguro ante descartes: handlePicapEvent relee el árbol de
     * ventanas, no usa el payload del evento; solo cambia la latencia (≤1 seg).
     */
    fun picapEventAllowed(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - picapFloodWindowStartMs >= PICAP_FLOOD_WINDOW_MS) {
            // Cierre de la ventana anterior: evalúa si la ráfaga bajó.
            if (picapThrottleActive && picapFloodCount <= PICAP_FLOOD_LOW) {
                picapThrottleActive = false
                Log.i(
                    SERVICE_TAG,
                    "PICAP-THROTTLE: eventos normalizados (${picapFloodCount}/seg); proceso completo reanudado " +
                            "(se descartaron ${picapEventsDropped} eventos durante la ráfaga)."
                )
                picapEventsDropped = 0
            }
            picapFloodWindowStartMs = now
            picapFloodCount = 0
        }
        picapFloodCount++
        if (!picapThrottleActive && picapFloodCount > PICAP_FLOOD_HIGH) {
            picapThrottleActive = true
            Log.i(
                SERVICE_TAG,
                "PICAP-THROTTLE: ráfaga detectada (${picapFloodCount} eventos en ${PICAP_FLOOD_WINDOW_MS} ms); " +
                        "limitando el procesamiento a 1 evento/seg."
            )
        }
        if (picapThrottleActive && now - picapLastProcessedMs < PICAP_THROTTLE_MIN_INTERVAL_MS) {
            picapEventsDropped++
            return false
        }
        picapLastProcessedMs = now
        return true
    }
}
