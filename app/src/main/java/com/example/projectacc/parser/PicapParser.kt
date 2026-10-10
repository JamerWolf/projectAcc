package com.example.projectacc.parser

import com.example.projectacc.model.PicapOrder

object PicapParser {

    private val kmParensRegex = Regex("\\(([\\d,\\.]+)\\s*(km|m)\\)")

    /**
     * Analiza la lista de textos para extraer los campos del modelo PicapOrder.
     */
    fun parseOrder(texts: List<String>): PicapOrder {
        var id = ""
        var ganancia = ""
        var tiempoRec = ""
        var dirRec = ""
        var tiempoEnt = ""
        var dirEnt = ""

        for (i in texts.indices) {
            val current = texts[i]

            // Extraer ID (formato "ID: 12345")
            if (current.startsWith("ID: ")) {
                id = current.replace("ID: ", "").trim()
            }

            // Ganancia: el valor está después de "Tu ganancia final"
            if (current == "Tu ganancia final" && i + 1 < texts.size) {
                ganancia = texts[i + 1]
            }

            // Recogida: Detecta "A X mins..."
            if (current.startsWith("A ") && current.contains("min") && i + 1 < texts.size) {
                tiempoRec = current
                dirRec = texts[i + 1]
            }

            // Entrega: Detecta "X min(s) (X km)" que NO empieza por "A "
            if ((current.contains("min (") || current.contains("mins (")) && !current.startsWith("A ")) {
                tiempoEnt = current
                // La dirección puede estar en i+1 o en i+2 (si el ID se interpone)
                if (i + 1 < texts.size) {
                    if (texts[i + 1].startsWith("ID: ")) {
                        // El ID se interpuso: extraer ID aquí y la dirección está en i+2
                        id = texts[i + 1].replace("ID: ", "").trim()
                        if (i + 2 < texts.size) {
                            dirEnt = texts[i + 2]
                        }
                    } else {
                        dirEnt = texts[i + 1]
                    }
                }
            }
        }
        // Servicio: texto aislado con el nombre (ej. "Cruz verde Mostrador").
        // Se excluyen las direcciones para evitar falsos positivos.
        val servicio = texts.firstOrNull {
            it.contains("cruz verde", ignoreCase = true) && it != dirRec && it != dirEnt
        } ?: ""
        // Km embebidos en los strings de tiempo: "A 9 mins (4.31 km)" / "12 min (3.4 km)".
        // extractKmFromPickup devuelve 999.0 como centinela de "no encontrado".
        val kmRec = extractKmFromPickup(tiempoRec).takeIf { it < 999.0 } ?: 0.0
        val kmEnt = extractKmFromPickup(tiempoEnt).takeIf { it < 999.0 } ?: 0.0
        return PicapOrder(id, ganancia, tiempoRec, dirRec, tiempoEnt, dirEnt, servicio, kmRec, kmEnt, System.currentTimeMillis())
    }

    /**
     * Identifica pedidos OMS: el servicio debe decir "Cruz Verde Integración"
     * (con o sin acento). Cualquier otro servicio no es OMS.
     */
    fun isOmsService(servicio: String): Boolean =
        servicio.contains("cruz verde", ignoreCase = true) &&
            (servicio.contains("integracion", ignoreCase = true) ||
                servicio.contains("integración", ignoreCase = true))

    /**
     * Extrae los kilómetros de recogida de un string como "A 9 mins (4.31 km)"
     */
    fun extractKmFromPickup(pickupText: String): Double {
        val match = kmParensRegex.find(pickupText)
        if (match != null) {
            val value = match.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return 999.0
            val unit = match.groupValues[2]
            return if (unit == "m") value / 1000.0 else value
        }
        return 999.0
    }
}
