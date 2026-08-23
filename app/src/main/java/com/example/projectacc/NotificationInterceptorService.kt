package com.example.projectacc

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.projectacc.floating.FloatingPopupManager
import com.example.projectacc.parser.WhatsAppParser

class NotificationInterceptorService : NotificationListenerService() {
    private val TAG = "NotificationInterceptor"

    private val plateRequestPatterns = listOf("placa", "vehiculo", "vehículo", "ascopec", "tarjeta de propietario")
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

        // === PICAP HANDLING ===
        if (packageName == "co.picap.passenger" || packageName == "co.picap.driver" || packageName == "co.picap.picap_pro") {
            handlePicapNotification(sbn)
            return
        }

        // === WHATSAPP HANDLING ===
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

        if (!title.startsWith("Nuevo servicio", ignoreCase = true)) {
            return
        }

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
        Log.d(TAG, "AUTOCLICK: Switch de notificaciones desactivado automáticamente.")
    }

    private fun handleWhatsAppNotification(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val extras = notification.extras

        val title = extras.getString("android.title") ?: extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getString("android.text") ?: extras.getCharSequence("android.text")?.toString() ?: ""

        val fullText = "$title $text"
        val lowerText = fullText.lowercase()
        Log.d(TAG, "WhatsApp notificación: titulo='$title', texto='$text'")

        // 1. AUTO-PLACA: Detectar solicitud de placa en chat privado
        if (OrderStateManager.isAutoPlateEnabled.value) {
            val plate = OrderStateManager.vehiclePlate.value
            if (plate.isNotEmpty()) {
                val isPlateRequest = plateRequestPatterns.any { pattern -> lowerText.contains(pattern) }
                if (isPlateRequest) {
                    Log.d(TAG, "AUTO-PLACA: Solicitud de placa detectada. Copiando placa al portapapeles...")
                    // Copy plate to clipboard - user will paste manually after opening chat
                    val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("whatsapp_response", plate)
                    clipboard.setPrimaryClip(clip)
                    clickNotification(notification)
                    return
                }
            }
        }

        // 2. AUTO-SERVICIO: Detectar notificación de servicio en grupo
        val service = WhatsAppParser.parse(fullText)
        if (service != null) {
            val scannedIds = OrderStateManager.scannedWhatsAppServiceIds.value
            if (scannedIds.contains(service.id)) {
                Log.d(TAG, "AUTO-SERVICIO: Servicio #${service.id} ya escaneado. Ignorando.")
                return
            }

            Log.d(TAG, "AUTO-SERVICIO: Servicio #${service.id} detectado en notificación. Mostrando popup...")

            OrderStateManager.setWhatsAppOrder(service)
            OrderStateManager.addScannedWhatsAppServiceId(service.id)

            if (floatingPopup?.canDrawOverlays() == true) {
                // Save the PendingIntent NOW (before user accepts)
                val savedContentIntent = notification.contentIntent

                floatingPopup?.show(service) { acceptedService ->
                    Log.d(TAG, "AUTO-SERVICIO: Popup aceptado para servicio #${acceptedService.id}")

                    // 1. Copy "Me interesa {id}" to clipboard
                    val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("whatsapp_response", "Me interesa ${acceptedService.id}")
                    clipboard.setPrimaryClip(clip)
                    Log.d(TAG, "AUTO-SERVICIO: Texto copiado al portapapeles")

                    // 2. Open the specific chat via saved PendingIntent
                    if (savedContentIntent != null) {
                        try {
                            savedContentIntent.send()
                            Log.d(TAG, "AUTO-SERVICIO: Chat abierto via contentIntent")
                        } catch (e: Exception) {
                            Log.e(TAG, "AUTO-SERVICIO: Error al abrir chat: ${e.message}")
                        }
                    } else {
                        Log.w(TAG, "AUTO-SERVICIO: contentIntent es null")
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

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No necesitamos hacer nada al eliminar notificaciones
    }
}
