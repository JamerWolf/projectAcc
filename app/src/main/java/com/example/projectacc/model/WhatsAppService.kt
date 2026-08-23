package com.example.projectacc.model

data class WhatsAppService(
    val id: String = "",
    val servicio: String = "",
    val empresa: String = "",
    val ciudad: String = "",
    val origen: String = "",
    val destino: String = "",
    val pago: String = "",
    val valorCobrar: String = "",
    val requisitos: String = ""
) {
    /**
     * Extracts "Medio de pago: X" from requisitos and adds formatted value.
     * Example: "El valor a cobrar es: 92205 Medio de pago: Pago efectivo"
     * Returns: "Medio de pago: Pago efectivo 92.205"
     * If no "Medio de pago" found, returns null.
     */
    fun medioDePagoFormatted(): String? {
        val match = Regex("Medio de pago:\\s*(.+)", RegexOption.IGNORE_CASE).find(requisitos) ?: return null
        val medioDePago = match.groupValues[1].trimEnd(' ', '.')

        val valor = extractValor() ?: return null
        val formattedValor = formatNumber(valor)

        return "$medioDePago $formattedValor"
    }

    /**
     * Extracts the numeric value from valorCobrar or requisitos.
     */
    fun extractValor(): Int? {
        val fromField = valorCobrar.replace(".", "").replace(",", "").replace("COP", "").trim().toIntOrNull()
        if (fromField != null && fromField > 0) return fromField

        val fromRequisitos = Regex("valor a cobrar es:\\s*(\\d+)").find(requisitos.lowercase())
            ?.groupValues?.get(1)?.toIntOrNull()
        return fromRequisitos
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
        val lowerRequisitos = requisitos.lowercase()
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
