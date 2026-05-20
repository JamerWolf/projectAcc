package com.example.projectacc

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationInterceptorService : NotificationListenerService() {
    private val TAG = "NotificationInterceptor"

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        // Solo procesar notificaciones de Picap
        val packageName = sbn.packageName
        if (packageName != "co.picap.passenger" && packageName != "co.picap.driver" && packageName != "co.picap.picap_pro") {
            return
        }

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

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No necesitamos hacer nada al eliminar notificaciones
    }
}
