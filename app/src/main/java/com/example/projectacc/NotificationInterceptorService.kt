package com.example.projectacc

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationInterceptorService : NotificationListenerService() {
    private val TAG = "NotificationInterceptor"

    private val plateRequestPatterns = listOf("placa", "vehiculo", "vehículo", "ascopec", "tarjeta de propietario")

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
        // Solo procesar si auto-placa está habilitado
        if (!OrderStateManager.isAutoPlateEnabled.value) return

        val plate = OrderStateManager.vehiclePlate.value
        if (plate.isEmpty()) return

        val notification = sbn.notification
        val extras = notification.extras

        // Extraer texto de la notificación
        val title = extras.getString("android.title") ?: extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getString("android.text") ?: extras.getCharSequence("android.text")?.toString() ?: ""

        val fullText = "$title $text".lowercase()
        Log.d(TAG, "WhatsApp notificación: titulo='$title', texto='$text'")

        // Verificar si contiene patrón de solicitud de placa
        val isPlateRequest = plateRequestPatterns.any { pattern -> fullText.contains(pattern) }
        if (!isPlateRequest) return

        Log.d(TAG, "AUTO-PLACA: Solicitud de placa detectada en notificación. Abriendo chat...")

        // Abrir el chat haciendo clic en la notificación
        try {
            val contentIntent = notification.contentIntent
            if (contentIntent != null) {
                contentIntent.send()
                Log.d(TAG, "AUTO-PLACA: Chat abierto. La placa se pegará automáticamente.")
            } else {
                Log.w(TAG, "AUTO-PLACA: La notificación no tiene contentIntent.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "AUTO-PLACA: Error al abrir chat: ${e.message}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No necesitamos hacer nada al eliminar notificaciones
    }
}
