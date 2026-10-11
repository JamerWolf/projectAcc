package com.example.projectacc

import android.app.PendingIntent

/**
 * Holder en memoria del último contentIntent de la notificación de Picap
 * "Nuevo servicio". Sirve de replay: desde la UI se puede re-enviar ese
 * PendingIntent para que Picap reabra la oferta aunque no llegue un pedido
 * nuevo (útil para probar en campo sin esperar).
 *
 * En memoria a propósito: si el proceso muere, el holder se vacía, pero la
 * próxima notificación "Nuevo servicio" lo vuelve a llenar. El PendingIntent
 * sigue siendo válido mientras Picap (el dueño) no se detenga.
 */
object PicapIntentHolder {
    @Volatile
    var lastContentIntent: PendingIntent? = null

    @Volatile
    var lastTitle: String? = null

    @Volatile
    var savedAt: Long = 0L
}
