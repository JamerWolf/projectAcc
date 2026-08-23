package com.example.projectacc.floating

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.example.projectacc.R
import com.example.projectacc.model.WhatsAppService

/**
 * Manages a floating popup window that appears over other apps.
 * Uses WindowManager with TYPE_APPLICATION_OVERLAY.
 */
class FloatingPopupManager(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var popupView: View? = null
    private var currentService: WhatsAppService? = null
    private var onAcceptCallback: ((WhatsAppService) -> Unit)? = null

    fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show(
        service: WhatsAppService,
        onAccept: (WhatsAppService) -> Unit
    ) {
        if (!canDrawOverlays()) return
        if (popupView != null) dismiss()

        currentService = service
        onAcceptCallback = onAccept

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Inflate the popup layout
        val inflater = LayoutInflater.from(context)
        popupView = inflater.inflate(R.layout.floating_whatsapp_popup, null)

        // Populate data
        popupView?.findViewById<TextView>(R.id.tvServiceId)?.text = "Servicio #${service.id}"
        popupView?.findViewById<TextView>(R.id.tvEmpresa)?.text = service.empresa
        popupView?.findViewById<TextView>(R.id.tvServicio)?.text = service.servicio
        popupView?.findViewById<TextView>(R.id.tvCiudad)?.text = service.ciudad
        popupView?.findViewById<TextView>(R.id.tvOrigen)?.text = service.origen
        popupView?.findViewById<TextView>(R.id.tvDestino)?.text = service.destino
        popupView?.findViewById<TextView>(R.id.tvPago)?.text = "Pago: ${service.pago}"
        popupView?.findViewById<TextView>(R.id.tvValor)?.text = service.valorCobrar

        // Accept button
        popupView?.findViewById<Button>(R.id.btnAccept)?.setOnClickListener {
            android.util.Log.d("FloatingPopup", "Boton Aceptar clickeado. Invocando callback...")
            onAcceptCallback?.invoke(service)
            android.util.Log.d("FloatingPopup", "Callback invocado. Cerrando popup...")
            dismiss()
            android.util.Log.d("FloatingPopup", "Popup cerrado.")
        }

        // Close button
        popupView?.findViewById<Button>(R.id.btnClose)?.setOnClickListener {
            dismiss()
        }

        // Window params
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        // Make draggable
        setupDrag(popupView!!, params)

        windowManager?.addView(popupView, params)
    }

    fun dismiss() {
        try {
            popupView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
        popupView = null
        currentService = null
        onAcceptCallback = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDrag(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }
}
