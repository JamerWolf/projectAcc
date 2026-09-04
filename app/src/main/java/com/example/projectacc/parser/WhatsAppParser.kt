package com.example.projectacc.parser

import com.example.projectacc.model.WhatsAppService

object WhatsAppParser {

    private val serviceIdRegex = Regex("🏷️\\s*Servicio\\s+(\\d+)")
    private val lastLineIdRegex = Regex("Me interesa\\s+(\\d+)")
    private val empresaRegex = Regex("(?:Nuevo servicio de|disponible en|servicio programado disponible en)\\s+(.+?)\\.\\s*$", RegexOption.MULTILINE)
    private val servicioTypeRegex = Regex("Servicio\\s+\\d+\\s*[—–-]\\s*(.+)")
    private val ciudadRegex = Regex("🏙️\\s*Ciudad:\\s*(.+)")
    private val origenRegex = Regex("📍\\s*Origen:\\s*(.+)")
    private val destinoRegex = Regex("🏁\\s*Destino:\\s*(.+)")
    private val valorFieldRegex = Regex("Valor:\\s*(.+)")
    private val pagoRegex = Regex("Pago:\\s*(.+)")
    private val valorRegex = Regex("El valor a cobrar es:\\s*(\\d+)")
    private val requisitosRegex = Regex("Requisitos:\\s*(.+)")

    fun parse(text: String): WhatsAppService? {
        // Strip WhatsApp bold formatting (*) and clean text
        val cleanText = text.replace("\r", "").replace("*", "")

        // Get ALL service IDs and take the LAST one (most recent message)
        val allServiceIds = serviceIdRegex.findAll(cleanText).map { it.groupValues[1] }.toList()
        val allLastIds = lastLineIdRegex.findAll(cleanText).map { it.groupValues[1] }.toList()

        val allIds = allServiceIds + allLastIds
        if (allIds.isEmpty()) return null

        val id = allIds.last()

        // Find the start of the LAST message block
        // Look for the last "⚡" or "Nuevo servicio" before the last service ID
        val lastIdIndex = cleanText.lastIndexOf("Servicio $id")
            .coerceAtLeast(cleanText.lastIndexOf("Me interesa $id"))

        // Search backwards from the last ID to find the message start
        val messageStart = findMessageStart(cleanText, lastIdIndex)

        // Parse from the message start
        val textToParse = if (messageStart >= 0) cleanText.substring(messageStart) else cleanText

        val empresa = empresaRegex.find(textToParse)?.groupValues?.get(1)?.trim() ?: ""
        val servicio = servicioTypeRegex.find(textToParse)?.groupValues?.get(1)?.trim() ?: ""
        val ciudad = ciudadRegex.find(textToParse)?.groupValues?.get(1)?.trim() ?: ""
        val origen = origenRegex.find(textToParse)?.groupValues?.get(1)?.trim() ?: ""
        val destino = destinoRegex.find(textToParse)?.groupValues?.get(1)?.trim() ?: ""
        val valor = valorFieldRegex.find(textToParse)?.groupValues?.get(1)?.trim() ?: ""
        val pago = pagoRegex.find(textToParse)?.groupValues?.get(1)?.trim() ?: ""
        val valorCobrar = valorRegex.find(textToParse)?.groupValues?.get(1)?.trim() ?: ""
        val requisitos = requisitosRegex.find(textToParse)?.groupValues?.get(1)?.trim() ?: ""

        if (id.isEmpty()) return null

        return WhatsAppService(
            id = id,
            servicio = servicio,
            empresa = empresa,
            ciudad = ciudad,
            origen = origen,
            destino = destino,
            valor = valor,
            pago = pago,
            valorCobrar = valorCobrar,
            requisitos = requisitos
        )
    }

    /**
     * Finds the start of the last message block by searching backwards from position.
     * Looks for ⚡, "Nuevo servicio", or start of text.
     */
    private fun findMessageStart(text: String, fromIndex: Int): Int {
        if (fromIndex <= 0) return 0

        val searchArea = text.substring(0, fromIndex)

        val lastLightning = searchArea.lastIndexOf("⚡")
        val lastNuevoServicio = searchArea.lastIndexOf("Nuevo servicio")
        val lastRuta = searchArea.lastIndexOf("🗺️")
        val lastProgramado = searchArea.lastIndexOf("🗓️")

        val markers = listOf(lastLightning, lastNuevoServicio, lastRuta, lastProgramado).filter { it >= 0 }
        return if (markers.isNotEmpty()) markers.max() else 0
    }
}
