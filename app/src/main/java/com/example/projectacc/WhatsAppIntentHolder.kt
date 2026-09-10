package com.example.projectacc

import android.app.PendingIntent

/**
 * Holds the WhatsApp notification's PendingIntent and text temporarily.
 * Used to forward the intent when WhatsAppForwardActivity launches.
 */
object WhatsAppIntentHolder {
    var pendingIntent: PendingIntent? = null
    var lastCopiedText: String? = null
    var notificationReceivedAt: Long = 0L

    fun clear() {
        pendingIntent = null
        lastCopiedText = null
        notificationReceivedAt = 0L
    }
}
