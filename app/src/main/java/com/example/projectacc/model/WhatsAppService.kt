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
     * Returns true if this service needs a return/warning icon.
     * Conditions: datafono, cadena de frio, caba, or value > 150000
     */
    fun needsReturnIcon(): Boolean {
        val lowerRequisitos = requisitos.lowercase()
        if (lowerRequisitos.contains("datafono") || lowerRequisitos.contains("datáfono")) return true
        if (lowerRequisitos.contains("cadena de frio") || lowerRequisitos.contains("cadena de frío")) return true
        if (lowerRequisitos.contains("caba")) return true
        if (lowerRequisitos.contains("refrigerado")) return true
        if (lowerRequisitos.contains("devolver")) return true

        val valor = valorCobrar.replace(".", "").replace(",", "").replace("COP", "").trim().toIntOrNull() ?: 0
        if (valor > 150000) return true

        return false
    }
}
