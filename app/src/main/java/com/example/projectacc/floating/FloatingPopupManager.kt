package com.example.projectacc.floating

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.location.Location
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.example.projectacc.R
import com.example.projectacc.location.LocationHelper
import com.example.projectacc.location.SavedLocationManager
import com.example.projectacc.model.WhatsAppService
import com.example.projectacc.OrderStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages a floating popup window that appears over other apps.
 * Uses WindowManager with TYPE_APPLICATION_OVERLAY.
 */
class FloatingPopupManager(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var popupView: View? = null
    private var currentService: WhatsAppService? = null
    private var onAcceptCallback: ((WhatsAppService) -> Unit)? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val locationHelper = LocationHelper(context)

    // Debug: store coordinates for route button
    private var currentLat: Double = 0.0
    private var currentLon: Double = 0.0
    private var targetLat: Double = 0.0
    private var targetLon: Double = 0.0
    private var destLat: Double = 0.0
    private var destLon: Double = 0.0

    fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show(
        service: WhatsAppService,
        onAccept: (WhatsAppService) -> Unit,
        autoAccept: Boolean = false
    ) {
        if (!canDrawOverlays()) return
        if (popupView != null) dismiss()

        currentService = service
        onAcceptCallback = onAccept

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Inflate the popup layout
        val inflater = LayoutInflater.from(context)
        popupView = inflater.inflate(R.layout.floating_whatsapp_popup, null)

        // Populate data - check servicio FIRST for Ruta/Programado, then empresa for Mostrador/OMS
        val shortTitle = when {
            service.servicio.contains("Ruta", ignoreCase = true) -> "Ruta"
            service.servicio.contains("Programado", ignoreCase = true) -> "Programado"
            service.empresa.contains("Integracion", ignoreCase = true) || service.empresa.contains("Integración", ignoreCase = true) -> "OMS"
            service.empresa.contains("Mostrador", ignoreCase = true) -> "Mostrador"
            else -> service.empresa
        }
        popupView?.findViewById<TextView>(R.id.tvServiceId)?.text = "$shortTitle #${service.id}"

        // Show return icon if needed
        val returnIcon = popupView?.findViewById<TextView>(R.id.tvReturnIcon)
        if (service.needsReturnIcon()) {
            returnIcon?.visibility = android.view.View.VISIBLE
        }

        // Calculate and show distance to origin
        val tvDistancia = popupView?.findViewById<TextView>(R.id.tvDistancia)
        val tvServiceId = popupView?.findViewById<TextView>(R.id.tvServiceId)
        val tvLocationName = popupView?.findViewById<TextView>(R.id.tvLocationName)

        if (service.origen.isNotEmpty()) {
            scope.launch {
                // Check for saved location match first
                val savedLocation = SavedLocationManager.findMatch(service.origen, context)
                val currentLocation = locationHelper.getCurrentLocation()

                if (savedLocation != null && currentLocation != null) {
                    // Use saved coordinates
                    currentLat = currentLocation.latitude
                    currentLon = currentLocation.longitude
                    targetLat = savedLocation.lat
                    targetLon = savedLocation.lon

                    Log.d("FloatingPopup", "Saved location match: ${savedLocation.name}")
                    Log.d("FloatingPopup", "Coords: from=$currentLat,$currentLon to=$targetLat,$targetLon")

                    tvLocationName?.text = savedLocation.name
                    tvLocationName?.visibility = android.view.View.VISIBLE
                } else if (service.origen.isNotEmpty()) {
                    // Fallback to geocoder
                    val targetLocation = locationHelper.geocodeAddress(service.origen)

                    if (currentLocation != null && targetLocation != null) {
                        currentLat = currentLocation.latitude
                        currentLon = currentLocation.longitude
                        targetLat = targetLocation.latitude
                        targetLon = targetLocation.longitude

                        Log.d("FloatingPopup", "Geocoded coords: from=$currentLat,$currentLon to=$targetLat,$targetLon")
                    }
                }

                // Calculate distance from my location to origin
                if (currentLat != 0.0 && targetLat != 0.0) {
                    val kmOrigen = locationHelper.getRouteDistanceKm(
                        currentLat, currentLon, targetLat, targetLon
                    )

                    // Show km origen next to valor
                    val tvKmOrigen = popupView?.findViewById<TextView>(R.id.tvKmOrigen)
                    if (kmOrigen != null) {
                        tvKmOrigen?.text = String.format("%.1f km", kmOrigen.toDouble())
                        tvKmOrigen?.visibility = android.view.View.VISIBLE
                    }

                    // Click opens Google Maps from my location to origin
                    tvKmOrigen?.setOnClickListener {
                        val uri = Uri.parse(
                            "https://www.google.com/maps/dir/?api=1" +
                            "&origin=$currentLat,$currentLon" +
                            "&destination=$targetLat,$targetLon" +
                            "&travelmode=driving"
                        )
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        intent.setPackage("com.google.android.apps.maps")
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try { context.startActivity(intent) }
                        catch (e: Exception) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        }
                    }

                    // Calculate km from origin to destination
                    var kmDestino: Float? = null
                    if (service.destino.isNotEmpty() && targetLat != 0.0) {
                        val tvKmDestino = popupView?.findViewById<TextView>(R.id.tvKmDestino)
                        val destLocation = locationHelper.geocodeAddress(service.destino)
                        if (destLocation != null) {
                            destLat = destLocation.latitude
                            destLon = destLocation.longitude
                            kmDestino = locationHelper.getRouteDistanceKm(
                                targetLat, targetLon,
                                destLat, destLon
                            )
                            if (kmDestino != null) {
                                tvKmDestino?.text = String.format("%.1f km", kmDestino.toDouble())
                                tvKmDestino?.visibility = android.view.View.VISIBLE

                                // Click opens route from origin to destination in Google Maps
                                tvKmDestino?.setOnClickListener {
                                    val uri = Uri.parse(
                                        "https://www.google.com/maps/dir/?api=1" +
                                        "&origin=$targetLat,$targetLon" +
                                        "&destination=$destLat,$destLon" +
                                        "&travelmode=driving"
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW, uri)
                                    intent.setPackage("com.google.android.apps.maps")
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    try { context.startActivity(intent) }
                                    catch (e: Exception) {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        })
                                    }
                                }
                            }
                        }
                    }

                    // Calculate total km for title
                    val hasReturn = service.needsReturnIcon()
                    val totalKm = (kmOrigen ?: 0f) + (kmDestino ?: 0f) + if (hasReturn && kmDestino != null) kmDestino else 0f

                    val displayTotal = if (totalKm > 0f) {
                        String.format("%.1f km", totalKm.toDouble())
                    } else {
                        val straightKm = android.location.Location("").apply {
                            latitude = currentLat; longitude = currentLon
                        }.distanceTo(android.location.Location("").apply {
                            latitude = targetLat; longitude = targetLon
                        }) / 1000f
                        String.format("~%.1f km", straightKm.toDouble())
                    }
                    tvDistancia?.text = displayTotal
                    tvDistancia?.visibility = android.view.View.VISIBLE

                    // Click opens full route: my location → origin → destination
                    tvDistancia?.setOnClickListener {
                        val uri = Uri.parse(
                            "https://www.google.com/maps/dir/?api=1" +
                            "&origin=$currentLat,$currentLon" +
                            "&destination=$destLat,$destLon" +
                            "&waypoints=$targetLat,$targetLon" +
                            "&travelmode=driving"
                        )
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        intent.setPackage("com.google.android.apps.maps")
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try { context.startActivity(intent) }
                        catch (e: Exception) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        }
                    }
                }
            }
        }

        popupView?.findViewById<TextView>(R.id.tvCiudad)?.text = service.ciudad
        popupView?.findViewById<TextView>(R.id.tvOrigen)?.text = service.origen
        popupView?.findViewById<TextView>(R.id.tvDestino)?.text = service.destino

        // Requisitos - highlight yellow if return conditions met
        val tvRequisitos = popupView?.findViewById<TextView>(R.id.tvRequisitos)
        val tvRequisitosLabel = popupView?.findViewById<TextView>(R.id.tvRequisitosLabel)
        if (service.requisitos.isNotEmpty()) {
            tvRequisitos?.text = service.requisitos
            if (service.needsReturnIcon()) {
                tvRequisitos?.setTextColor(android.graphics.Color.parseColor("#FFC107"))
                tvRequisitosLabel?.setTextColor(android.graphics.Color.parseColor("#FFC107"))
            }
        } else {
            tvRequisitosLabel?.visibility = android.view.View.GONE
            tvRequisitos?.visibility = android.view.View.GONE
        }

        // Medio de pago + valor formateado
        val tvMedioPago = popupView?.findViewById<TextView>(R.id.tvMedioPago)
        val medioPago = service.medioDePagoFormatted()
        if (medioPago != null) {
            tvMedioPago?.text = medioPago
            tvMedioPago?.visibility = android.view.View.VISIBLE
        }

        // Valor a cobrar
        val tvValor = popupView?.findViewById<TextView>(R.id.tvValor)
        val valorText = service.valorFormatted()
        if (valorText != null) {
            tvValor?.text = "💰 $valorText"
            tvValor?.visibility = android.view.View.VISIBLE
        }

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

        // Route button - from origin to destination (text addresses)
        popupView?.findViewById<Button>(R.id.btnRoute)?.setOnClickListener {
            val uri = Uri.parse(
                "https://www.google.com/maps/dir/?api=1" +
                "&origin=${Uri.encode(service.origen)}" +
                "&destination=${Uri.encode(service.destino)}" +
                "&travelmode=driving"
            )
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage("com.google.android.apps.maps")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, uri)
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallbackIntent)
            }
            Log.d("FloatingPopup", "Ruta: $currentLat,$currentLon -> ${service.origen}")
        }

        // Window params - full width minus 10px margin each side
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val marginPx = (10 * displayMetrics.density).toInt()
        val popupWidth = screenWidth - (marginPx * 2)

        val params = WindowManager.LayoutParams(
            popupWidth,
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

        // Play popup sound if enabled
        if (OrderStateManager.isPopupSoundEnabled.value) {
            try {
                val uri = Uri.parse("android.resource://${context.packageName}/${R.raw.popup_sound}")
                val mediaPlayer = MediaPlayer.create(context, uri)
                mediaPlayer?.setOnCompletionListener { it.release() }
                mediaPlayer?.start()
            } catch (e: Exception) {
                android.util.Log.e("FloatingPopup", "Error playing sound: ${e.message}")
            }
        }

        // Auto-accept if enabled
        if (autoAccept) {
            Handler(Looper.getMainLooper()).postDelayed({
                popupView?.findViewById<Button>(R.id.btnAccept)?.performClick()
                Log.d("FloatingPopup", "Auto-accept triggered for service #${service.id}")
            }, 500)
        }
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
