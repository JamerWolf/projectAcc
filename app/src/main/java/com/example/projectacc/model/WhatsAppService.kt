package com.example.projectacc.model

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
    val requisitos: String = ""
) {
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

        val valor = extractValor() ?: return null
        val formattedValor = formatNumber(valor)

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

    fun needsReturnIcon(): Boolean {
        // Si el medio de pago es prepagado, NUNCA mostrar ícono de retorno
        val lowerPago = pago.lowercase()
        val lowerRequisitos = requisitos.lowercase()
        if (lowerPago.contains("prepagado") || lowerRequisitos.contains("prepagado")) return false

        if (lowerRequisitos.contains("datafono") || lowerRequisitos.contains("datáfono")) return true
        if (lowerRequisitos.contains("cadena de frio") || lowerRequisitos.contains("cadena de frío")) return true
        if (lowerRequisitos.contains("caba")) return true
        if (lowerRequisitos.contains("refrigerado")) return true
        if (lowerRequisitos.contains("devolver")) return true

        val valor = extractValor() ?: 0
        if (valor > 150000) return true

        return false
    }
}
