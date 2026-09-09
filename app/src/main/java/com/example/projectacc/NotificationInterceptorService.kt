package com.example.projectacc

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.projectacc.floating.FloatingPopupManager
import com.example.projectacc.parser.WhatsAppParser

class NotificationInterceptorService : NotificationListenerService() {
    private val TAG = "NotificationInterceptor"

    private val plateRequestPatterns = listOf("placa", "vehiculo", "vehículo", "ascopec", "tarjeta de propietario")
    private val plateRequestExactPhrases = listOf("envíame la placa de tu vehículo", "enviame la placa de tu vehiculo")
    private var floatingPopup: FloatingPopupManager? = null

    override fun onCreate() {
        super.onCreate()
        floatingPopup = FloatingPopupManager(this)
    }

    override fun onDestroy() {
        floatingPopup?.dismiss()
        floatingPopup = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val packageName = sbn.packageName

        if (packageName == "co.picap.passenger" || packageName == "co.picap.driver" || packageName == "co.picap.picap_pro") {
            handlePicapNotification(sbn)
            return
        }

        if (packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b") {
            handleWhatsAppNotification(sbn)
            return
        }
    }

    private fun handlePicapNotification(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getString("android.title") ?: extras.getCharSequence("android.title")?.toString() ?: ""

        Log.d(TAG, "Notificación de Picap: titulo='$title'")

        if (!title.startsWith("Nuevo servicio", ignoreCase = true)) return

        if (!OrderStateManager.isNotificationClickEnabled.value) {
            Log.d(TAG, "Switch de notificaciones DESACTIVADO. Ignorando.")
            return
        }

        Log.d(TAG, "AUTOCLICK: Detectada notificación 'Nuevo servicio'. Ejecutando clic...")

        try {
            val contentIntent = notification.contentIntent
            if (contentIntent != null) {
                contentIntent.send()
                Log.d(TAG, "AUTOCLICK: contentIntent ejecutado exitosamente.")
            } else {
                Log.w(TAG, "AUTOCLICK: La notificación no tiene contentIntent.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "AUTOCLICK: Error al ejecutar contentIntent: ${e.message}")
        }

        OrderStateManager.setNotificationClickEnabled(false)
    }

    private fun handleWhatsAppNotification(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val extras = notification.extras

        val title = extras.getString("android.title") ?: extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getString("android.text") ?: extras.getCharSequence("android.text")?.toString() ?: ""

        val fullText = "$title $text"
        val lowerText = fullText.lowercase()
        Log.d(TAG, "WhatsApp notificación: titulo='$title', texto='$text'")

        // 1. AUTO-PLACA
        if (OrderStateManager.isAutoPlateEnabled.value) {
            val plate = OrderStateManager.vehiclePlate.value
            if (plate.isNotEmpty()) {
                val isPlateRequest = plateRequestPatterns.any { pattern -> lowerText.contains(pattern) }
                val isExactPhrase = plateRequestExactPhrases.any { phrase -> lowerText.contains(phrase) }
                if (isPlateRequest || isExactPhrase) {
                    Log.d(TAG, "AUTO-PLACA: Solicitud de placa detectada. Abriendo chat y pegando placa...")

                    // Copy plate to clipboard
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("whatsapp_response", plate)
                    clipboard.setPrimaryClip(clip)

                    // Save PendingIntent and launch forward activity
                    // Auto-send if exact phrase match, otherwise just paste
                    val savedContentIntent = notification.contentIntent
                    if (savedContentIntent != null) {
                        WhatsAppIntentHolder.pendingIntent = savedContentIntent
                        WhatsAppIntentHolder.lastCopiedText = plate
                        val forwardIntent = Intent(this, WhatsAppForwardActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            putExtra(WhatsAppForwardActivity.EXTRA_AUTO_SEND, isExactPhrase)
                        }
                        startActivity(forwardIntent)
                        Log.d(TAG, "AUTO-PLACA: WhatsAppForwardActivity lanzada (auto-send: $isExactPhrase)")
                    }
                    return
                }
            }
        }

        // Si el mensaje contiene "placa", NO es una oferta de servicio - ignorar
        if (lowerText.contains("placa")) {
            Log.d(TAG, "Mensaje contiene 'placa'. No es oferta de servicio. Ignorando.")
            return
        }

        // 2. AUTO-SERVICIO - Only if switch is enabled
        if (!OrderStateManager.isGroupAutoRespondEnabled.value) {
            Log.d(TAG, "AUTO-SERVICIO: Switch desactivado. Ignorando servicio.")
            return
        }

        val service = WhatsAppParser.parse(fullText)
        if (service != null) {
            val scannedIds = OrderStateManager.scannedWhatsAppServiceIds.value
            if (scannedIds.contains(service.id)) {
                Log.d(TAG, "AUTO-SERVICIO: Servicio #${service.id} ya escaneado. Ignorando.")
                return
            }

            Log.d(TAG, "AUTO-SERVICIO: Servicio #${service.id} detectado. Mostrando popup...")

            OrderStateManager.setWhatsAppOrder(service)
            OrderStateManager.addScannedWhatsAppServiceId(service.id)

            if (floatingPopup?.canDrawOverlays() == true) {
                val savedContentIntent = notification.contentIntent

                floatingPopup?.show(service) { acceptedService ->
                    Log.d(TAG, "AUTO-SERVICIO: Popup aceptado para #${acceptedService.id}")

                    val textToPaste = "Me interesa ${acceptedService.id}"

                    // 1. Copiar al portapapeles
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("whatsapp_response", textToPaste)
                    clipboard.setPrimaryClip(clip)

                    // 2. Guardar PendingIntent y texto, abrir WhatsApp
                    if (savedContentIntent != null) {
                        WhatsAppIntentHolder.pendingIntent = savedContentIntent
                        WhatsAppIntentHolder.lastCopiedText = textToPaste
                        val forwardIntent = Intent(this, WhatsAppForwardActivity::class.java)
                        forwardIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(forwardIntent)
                        Log.d(TAG, "AUTO-SERVICIO: WhatsAppForwardActivity lanzada")
                    }
                }
            }
        }
    }

    private fun clickNotification(notification: android.app.Notification) {
        try {
            val contentIntent = notification.contentIntent
            if (contentIntent != null) {
                contentIntent.send()
                Log.d(TAG, "Notificación clickeada exitosamente.")
            } else {
                Log.w(TAG, "La notificación no tiene contentIntent.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al clickear notificación: ${e.message}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}
