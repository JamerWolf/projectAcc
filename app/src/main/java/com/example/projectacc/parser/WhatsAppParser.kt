package com.example.projectacc.parser

import com.example.projectacc.model.WhatsAppService

object WhatsAppParser {

    private val serviceIdRegex = Regex("🏷️\\s*Servicio\\s+(\\d+)")
    private val lastLineIdRegex = Regex("Me interesa\\s+(\\d+)")
    private val empresaRegex = Regex("Nuevo servicio de\\s+(.+?)\\.")
    private val servicioTypeRegex = Regex("Servicio\\s+\\d+\\s*[—–-]\\s*(.+)")
    private val ciudadRegex = Regex("🏙️\\s*Ciudad:\\s*(.+)")
    private val origenRegex = Regex("📍\\s*Origen:\\s*(.+)")
    private val destinoRegex = Regex("🏁\\s*Destino:\\s*(.+)")
    private val pagoRegex = Regex("Pago:\\s*(.+)")
    private val valorRegex = Regex("El valor a cobrar es:\\s*(.+?)\\s*\\.")
    private val requisitosRegex = Regex("Requisitos:\\s*(.+)")

    fun parse(text: String): WhatsAppService? {
        val cleanText = text.replace("\r", "")

        // Required: service ID from either "🏷️ Servicio {id}" or "Me interesa {id}"
        val idMatch = serviceIdRegex.find(cleanText) ?: lastLineIdRegex.find(cleanText)
        val id = idMatch?.groupValues?.get(1) ?: return null

        val empresa = empresaRegex.find(cleanText)?.groupValues?.get(1)?.trim() ?: ""
        val servicio = servicioTypeRegex.find(cleanText)?.groupValues?.get(1)?.trim() ?: ""
        val ciudad = ciudadRegex.find(cleanText)?.groupValues?.get(1)?.trim() ?: ""
        val origen = origenRegex.find(cleanText)?.groupValues?.get(1)?.trim() ?: ""
        val destino = destinoRegex.find(cleanText)?.groupValues?.get(1)?.trim() ?: ""
        val pago = pagoRegex.find(cleanText)?.groupValues?.get(1)?.trim() ?: ""
        val valor = valorRegex.find(cleanText)?.groupValues?.get(1)?.trim() ?: ""
        val requisitos = requisitosRegex.find(cleanText)?.groupValues?.get(1)?.trim() ?: ""

        if (id.isEmpty()) return null

        return WhatsAppService(
            id = id,
            servicio = servicio,
            empresa = empresa,
            ciudad = ciudad,
            origen = origen,
            destino = destino,
            pago = pago,
            valorCobrar = valor,
            requisitos = requisitos
        )
    }
}
