package com.example.projectacc.model

data class Cobro(
    val valor: Int,
    val medioDePago: String
)

data class WhatsAppService(
    val id: String = "",
    val servicio: String = "",
    val empresa: String = "",
    val ciudad: String = "",
    val origen: String = "",
    val destino: String = "",
    val valor: String = "",
    val pago: String = "",
    val valorCobrar: String = "",
    val requisitos: String = "",
    val formasDePago: List<Cobro> = emptyList()
) {
    /**
     * Returns the pilot's payment amount.
     * Servicio format: from "Valor: $22.000"
     * Ruta format: from "Pago: $23.400"
     */
    fun pagoPiloto(): String? {
        // Servicio format: "Valor: $22.000" contains a $ amount
        if (valor.contains("$")) return valor
        // Ruta format: "Pago: $23.400" contains a $ amount
        if (pago.contains("$")) return pago
        return null
    }

    /**
     * Returns the payment method.
     * Servicio format: from "Pago: Billetera"
     * Ruta format: from "Forma de pago: Billetera" in requisitos
     */
    fun metodoPago(): String? {
        // Servicio format: "Pago: Billetera" (no $ amount)
        if (pago.isNotEmpty() && !pago.contains("$")) return pago
        // Ruta format: "Forma de pago: Billetera" in requisitos
        val formaMatch = Regex("Forma de pago:\\s*(.+)", RegexOption.IGNORE_CASE).find(requisitos)
        return formaMatch?.groupValues?.get(1)?.trim()
    }

    /**
     * Returns the "Valor" field formatted for display.
     * Example: "$7.480"
     */
    fun valorFormatted(): String? {
        if (valor.isEmpty()) return null
        return valor
    }

    /**
     * Extracts "Medio de pago: X" from requisitos and adds formatted value.
     * Example: "El valor a cobrar es: 92205 Medio de pago: Pago efectivo"
     * Returns: "Medio de pago: Pago efectivo 92.205"
     * If no "Medio de pago" found, returns null.
     */
    fun medioDePagoFormatted(): String? {
        val match = Regex("Medio de pago:\\s*([^.,]+)", RegexOption.IGNORE_CASE).find(requisitos) ?: return null
        val medioDePago = match.groupValues[1].trim()

        // Extract "valor a cobrar" from requisitos (e.g. "El valor a cobrar es: 301512")
        val cobrarMatch = Regex("valor a cobrar es:\\s*([\\d.]+)", RegexOption.IGNORE_CASE).find(requisitos)
        val valorCobrar = cobrarMatch?.groupValues?.get(1)?.replace(".", "")?.toIntOrNull()
            ?: extractValor()
            ?: return null
        val formattedValor = formatNumber(valorCobrar)

        return "$medioDePago $formattedValor"
    }

    /**
     * Extracts the numeric value from the valor field (e.g. "$19.485").
     */
    fun extractValor(): Int? {
        val fromValor = valor.replace("$", "").replace(".", "").replace(",", "").replace("COP", "").trim().toIntOrNull()
        if (fromValor != null && fromValor > 0) return fromValor

        return null
    }

    private fun formatNumber(num: Int): String {
        val str = num.toString()
        val formatted = StringBuilder()
        var count = 0
        for (i in str.length - 1 downTo 0) {
            if (count > 0 && count % 3 == 0) formatted.insert(0, ".")
            formatted.insert(0, str[i])
            count++
        }
        return formatted.toString()
    }

    fun cobrosFormatted(): List<String> {
        return formasDePago.map { cobro ->
            "${cobro.medioDePago} $${formatNumber(cobro.valor)}"
        }
    }

    /**
     * Extracts the cash payment amount (pago en efectivo) from cobros or requisitos.
     * Returns the numeric value or null if not found / not cash.
     */
    private fun extractCashAmount(): Int? {
        // Ruta format: check formasDePago for an "efectivo" cobro
        if (formasDePago.isNotEmpty()) {
            val cashCobro = formasDePago.find { it.medioDePago.lowercase().contains("efectivo") }
            if (cashCobro != null) return cashCobro.valor
            return null
        }

        // Regular format: check "Medio de pago" + "valor a cobrar" in requisitos
        val lowerRequisitos = requisitos.lowercase()
        val isEfectivo = lowerRequisitos.contains("efectivo")
        if (!isEfectivo) return null

        val cobrarMatch = Regex("valor a cobrar es:\\s*([\\d.]+)", RegexOption.IGNORE_CASE).find(requisitos)
        return cobrarMatch?.groupValues?.get(1)?.replace(".", "")?.toIntOrNull()
    }

    fun needsReturnIcon(): Boolean {
        val lowerPago = pago.lowercase()
        val lowerRequisitos = requisitos.lowercase()

        if (formasDePago.isNotEmpty()) {
            val allPrepaid = formasDePago.all { it.medioDePago.lowercase().contains("prepagado") }
            if (allPrepaid) return false
        } else {
            if (lowerPago.contains("prepagado") || lowerRequisitos.contains("prepagado")) return false
        }

        if (lowerRequisitos.contains("datafono") || lowerRequisitos.contains("datáfono")) return true
        if (lowerRequisitos.contains("cadena de frio") || lowerRequisitos.contains("cadena de frío")) return true
        if (lowerRequisitos.contains("caba")) return true
        if (lowerRequisitos.contains("refrigerado")) return true
        if (lowerRequisitos.contains("devolver")) return true

        // Return icon when cash payment > $150.000
        val cashAmount = extractCashAmount() ?: 0
        if (cashAmount > 150000) return true

        return false
    }
}
