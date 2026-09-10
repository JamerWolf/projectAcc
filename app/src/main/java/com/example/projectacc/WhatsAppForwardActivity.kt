package com.example.projectacc

import android.app.Activity
import android.app.ActivityOptions
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Transparent activity that forwards a saved PendingIntent to WhatsApp.
 * Polls for text input and pastes+sends (or just pastes) as soon as detected.
 */
class WhatsAppForwardActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var pollRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pendingIntent = WhatsAppIntentHolder.pendingIntent
        val textToSend = WhatsAppIntentHolder.lastCopiedText
        val autoSend = intent.getBooleanExtra(EXTRA_AUTO_SEND, true)
        WhatsAppIntentHolder.pendingIntent = null
        WhatsAppIntentHolder.lastCopiedText = null

        if (pendingIntent != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val options = ActivityOptions.makeBasic().apply {
                        setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        )
                    }
                    pendingIntent.send(this, 0, null, null, null, null, options.toBundle())
                } else {
                    pendingIntent.send()
                }
                Log.d("WhatsAppForward", "PendingIntent ejecutado (autoSend=$autoSend)")

                if (!textToSend.isNullOrEmpty()) {
                    startPollingForInput(textToSend, autoSend)
                } else {
                    finish()
                }
            } catch (e: Exception) {
                Log.e("WhatsAppForward", "Error: ${e.message}")
                finish()
            }
        } else {
            finish()
        }
    }

    private fun startPollingForInput(text: String, autoSend: Boolean) {
        var attempts = 0
        val maxAttempts = 30

        pollRunnable = object : Runnable {
            override fun run() {
                attempts++

                val accessibilityService = MyAccessibilityService.instance
                if (accessibilityService != null && accessibilityService.hasTextInput()) {
                    Log.d("WhatsAppForward", "Campo de texto detectado en intento #$attempts")
                    if (autoSend) {
                        accessibilityService.pasteAndSend(text)
                    } else {
                        accessibilityService.pasteOnly(text)
                    }
                    finish()
                    return
                }

                if (attempts >= maxAttempts) {
                    Log.w("WhatsAppForward", "Timeout después de $maxAttempts intentos")
                    finish()
                    return
                }

                handler.postDelayed(this, 100)
            }
        }

        handler.postDelayed(pollRunnable!!, 0)
    }

    override fun onDestroy() {
        pollRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_AUTO_SEND = "auto_send"
    }
}
