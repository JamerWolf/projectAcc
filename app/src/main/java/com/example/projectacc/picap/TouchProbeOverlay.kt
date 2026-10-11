package com.example.projectacc.picap

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.example.projectacc.SERVICE_TAG

/**
 * Sonda táctil de diagnóstico: overlay transparente a pantalla completa,
 * activo solo mientras hay un popup de orden de Picap vivo. Registra las
 * coordenadas de pantalla de los taps que recibe ([lastTapX]/[lastTapY]) y
 * devuelve el evento sin consumirlo (la vista no es clickable, así que
 * super.onTouchEvent retorna false para ACTION_DOWN y el input pasa a la
 * ventana de abajo: Picap sigue recibiendo el toque normalmente).
 *
 * Objetivo: comparar la posición REAL del dedo del usuario contra los bounds
 * del botón "Aceptar" que reporta el árbol de accesibilidad. Si difieren, los
 * bounds del a11y mienten y el tap de gesto apunta al lugar equivocado.
 *
 * THREADING: show/hide en Main (WindowManager); lastTapX/Y se escribe en Main
 * (listener de touch) y se lee/consume desde el pipeline (Default) — volatile
 * + consumo único alcanza.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class TouchProbeOverlay(private val context: Context) {

    // WindowManager lazy: el probe se construye en el <init> del servicio
    // (field initializer) y getSystemService falla antes de onCreate().
    private var windowManager: WindowManager? = null
    private var view: View? = null

    @Volatile
    private var lastTapX: Float = -1f

    @Volatile
    private var lastTapY: Float = -1f

    /** Último tap visto y borrado (consumo único). Seguro desde cualquier thread. */
    fun consumeLastTap(): Pair<Float, Float>? {
        val x = lastTapX
        if (x < 0f) return null
        val y = lastTapY
        lastTapX = -1f
        return x to y
    }

    /** Idempotente; debe invocarse en Main. */
    fun show() {
        if (view != null) return
        val wm = windowManager ?: run {
            context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }.also { windowManager = it }
        val probe = object : View(context) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    lastTapX = event.rawX
                    lastTapY = event.rawY
                    Log.i(
                        SERVICE_TAG,
                        "TAP-PROBE: tap visto por la sonda en (${event.rawX}, ${event.rawY})."
                    )
                }
                // super en vista no-clicable = false en DOWN → el toque pasa a Picap.
                return super.onTouchEvent(event)
            }
        }
        // FLAG_LAYOUT_IN_SCREEN: el overlay cubre toda la pantalla (barras de
        // sistema incluidas) y las coordenadas de la vista == coordenadas de
        // pantalla, el mismo espacio que getBoundsInScreen/drawFrame.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.START
        wm.addView(probe, params)
        view = probe
    }

    /** Idempotente; debe invocarse en Main. */
    fun hide() {
        val probe = view ?: return
        view = null
        try {
            windowManager?.removeView(probe)
        } catch (e: Exception) {
            Log.w(SERVICE_TAG, "TAP-PROBE: error removiendo sonda: ${e.message}")
        }
    }
}
