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

        // Solo reaccionar si el título empieza con "Nuevo servicio"
        if (!title.startsWith("Nuevo servicio", ignoreCase = true)) {
            return
        }

        // Verificar si el switch está activado
        if (!OrderStateManager.isNotificationClickEnabled.value) {
            Log.d(TAG, "Switch de notificaciones DESACTIVADO. Ignorando.")
            return
        }

        Log.d(TAG, "AUTOCLICK: Detectada notificación 'Nuevo servicio'. Ejecutando clic...")

        // Ejecutar el contentIntent de la notificación (abre Picap en el popup de la orden)
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

        // Auto-desactivar el switch (un solo tiro)
        OrderStateManager.setNotificationClickEnabled(false)
        Log.d(TAG, "AUTOCLICK: Switch de notificaciones desactivado automáticamente.")
    }

    private fun handleWhatsAppNotification(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val extras = notification.extras

        // Extraer texto de la notificación
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
                    Log.d(TAG, "AUTO-PLACA: Solicitud de placa detectada. Abriendo chat...")
                    clickNotification(notification)
                    return
                }
            }
        }

        // 2. AUTO-SERVICIO: Detectar notificación de servicio en grupo
        // Intentar parsear el texto de la notificación como servicio
        val service = WhatsAppParser.parse(fullText)
        if (service != null) {
            // Verificar si ya fue escaneado
            val scannedIds = OrderStateManager.scannedWhatsAppServiceIds.value
            if (scannedIds.contains(service.id)) {
                Log.d(TAG, "AUTO-SERVICIO: Servicio #${service.id} ya escaneado. Ignorando.")
                return
            }

            Log.d(TAG, "AUTO-SERVICIO: Servicio #${service.id} detectado en notificación. Mostrando popup...")

            // Guardar en estado
            OrderStateManager.setWhatsAppOrder(service)
            OrderStateManager.addScannedWhatsAppServiceId(service.id)

            // Mostrar popup flotante directamente desde la notificación
            if (floatingPopup?.canDrawOverlays() == true) {
                floatingPopup?.show(service) { acceptedService ->
                    // El popup fue aceptado - pegar "Me interesa {id}"
                    val myAccessibilityService = MyAccessibilityService.instance
                    if (myAccessibilityService != null) {
                        // Usar el método del servicio de accesibilidad para pegar
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            MyAccessibilityService.instance?.pasteFromNotification("Me interesa ${acceptedService.id}")
                        }, 500)
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
