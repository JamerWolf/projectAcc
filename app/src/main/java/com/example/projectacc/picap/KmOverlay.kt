package com.example.projectacc.picap

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import com.example.projectacc.SERVICE_TAG
import com.example.projectacc.a11y.AccessibilityTree
import com.example.projectacc.model.PicapOrder
import kotlin.math.roundToInt

// ============================================================
// OVERLAY DE KM TOTALES (junto al nodo del precio)
// ============================================================

/**
 * Window overlay (TYPE_APPLICATION_OVERLAY) showing total km next to the price
 * node.
 *
 * THREADING CONTRACT: [showKmOverlay], [hideKmOverlay] and [updateKmOverlay]
 * use WindowManager and MUST always run on the main thread. Callers on
 * Dispatchers.Default must post the call to main (see
 * PicapOfferCloser.hideKmOverlayOnMain).
 *
 * @param context the owning [android.app.Service]; used for WindowManager,
 * resources and the overlay permission check.
 */
class KmOverlay(private val context: Context) {

    private var kmOverlayTextView: TextView? = null
    private var kmOverlayAttached = false

    /**
     * Muestra/actualiza u oculta el overlay de km totales según el estado
     * de la orden actual y la posición del nodo del precio ("X.XXX COP").
     */
    fun updateKmOverlay(rootNode: AccessibilityNodeInfo, order: PicapOrder) {
        val totalKm = order.kmRecogida + order.kmEntrega
        if (totalKm <= 0.0 || order.ganancia.isEmpty()) {
            hideKmOverlay()
            return
        }
        // El nodo del precio es el mismo del que se extrajo la ganancia
        val anchor = AccessibilityTree.findNodeBoundsByContentDescription(rootNode, order.ganancia)
        if (anchor == null) {
            hideKmOverlay()
            return
        }
        showKmOverlay(anchor, totalKm)
    }

    private fun showKmOverlay(anchor: Rect, totalKm: Double) {
        try {
            if (!Settings.canDrawOverlays(context)) {
                Log.w(SERVICE_TAG, "KM_OVERLAY: permiso de overlay no concedido; no se muestra")
                return
            }
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val density = context.resources.displayMetrics.density
            val marginPx = (8 * density).roundToInt()

            val tv = kmOverlayTextView ?: TextView(context).apply {
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
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
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
                val sbResId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
                val sbH = if (sbResId > 0) context.resources.getDimensionPixelSize(sbResId) else 0
                Log.d(
                    SERVICE_TAG,
                    "KM_OVERLAY: visible total=${String.format("%.1f", totalKm)} km y=${params.y} h=${tv.measuredHeight} sb=$sbH rect=${anchor.toShortString()}"
                )
            }
        } catch (e: Exception) {
            Log.e(SERVICE_TAG, "KM_OVERLAY: error mostrando overlay: ${e.message}")
        }
    }

    fun hideKmOverlay() {
        if (!kmOverlayAttached) return
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            kmOverlayTextView?.let { wm.removeViewImmediate(it) }
            Log.d(SERVICE_TAG, "KM_OVERLAY: ocultado")
        } catch (e: Exception) {
            Log.w(SERVICE_TAG, "KM_OVERLAY: error al ocultar: ${e.message}")
        }
        kmOverlayAttached = false
    }
}
