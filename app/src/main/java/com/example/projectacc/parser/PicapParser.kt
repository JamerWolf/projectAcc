package com.example.projectacc.parser

import android.content.Context
import com.example.projectacc.location.SavedLocationManager
import com.example.projectacc.model.PicapOrder

object PicapParser {

    private val kmParensRegex = Regex("\\(([\\d,\\.]+)\\s*(km|m)\\)")

    /**
     * Ancla del ID de la oferta. Picap la renderizaba como "ID: abc123"; la UI
     * de 2026-11 la muestra como "ID abc123" (sin dos puntos), también dentro
     * de un contentDescription más largo ("Mensajería ID abc123"). Única
     * definición de la ancla: la usan el parser, el gate de porcentaje, el
     * cierre no-CV y el auto-clic en lista.
     */
    private val orderIdRegex = Regex("\\bID[:\\s]\\s*([A-Za-z0-9_-]+)")

    /** Primera parte de la línea de tiempo que Picap rotula como recogida. */
    private val pickupLabelRegex = Regex("\\b(recoger|recogida)\\b", RegexOption.IGNORE_CASE)

    /** Primera parte de la línea de tiempo que Picap rotula como entrega. */
    private val deliveryLabelRegex = Regex("\\b(entregar|entrega)\\b", RegexOption.IGNORE_CASE)

    /**
     * Extrae el ID de una oferta de un texto del árbol, con o sin dos puntos.
     * Devuelve "" si el texto no es un ancla de ID.
     */
    fun extractOrderId(text: String): String =
        orderIdRegex.find(text)?.groupValues?.get(1) ?: ""

    /** true si [text] es un nodo con el ID de la oferta. */
    fun isOrderIdNode(text: String): Boolean = extractOrderId(text).isNotEmpty()

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

            // ID: "ID: 12345" (UI hasta 2026-10) y "ID 12345" (UI 2026-11).
            val lineId = extractOrderId(current)
            if (lineId.isNotEmpty()) {
                id = lineId
            }

            // Ganancia: el valor está después de "Tu ganancia final"
            if (current == "Tu ganancia final" && i + 1 < texts.size) {
                ganancia = texts[i + 1]
            }

            // Línea de tiempo: lleva minutos y el km entre paréntesis, en
            // cualquiera de las formas conocidas:
            //   "A 9 mins (4.31 km)"            (UI hasta 2026-10)
            //   "12 min (3.4 km)"               (entrega clásica)
            //   "A menos de un minuto (890m)"   (entrega corta vieja)
            //   "Menos de un minuto (259.9 m)"  (entrega corta 2026-10)
            //   "Recoger · a 11 mins (4.76 km)" (UI 2026-11)
            //   "Entregar · 11 min (15.1 km)"   (UI 2026-11)
            val hasMinutes = current.contains("min", ignoreCase = true)
            val labeledPickup = hasMinutes && pickupLabelRegex.containsMatchIn(current)
            val labeledDelivery = hasMinutes && deliveryLabelRegex.containsMatchIn(current)
            // Las etiquetas "Recoger"/"Entregar" de la UI 2026-11 deciden el
            // papel de la línea; sin etiquetas se conserva el orden histórico
            // (la primera línea de tiempo es la recogida, la siguiente la
            // entrega), probado en los tests de las UIs anteriores.
            val isTimeLine = labeledPickup || labeledDelivery ||
                (hasMinutes && kmParensRegex.containsMatchIn(current))
            val isPickup = labeledPickup || (isTimeLine && !labeledDelivery && tiempoRec.isEmpty())

            if (isPickup && i + 1 < texts.size) {
                tiempoRec = current
                dirRec = texts[i + 1]
            } else if (isTimeLine) {
                tiempoEnt = current
                // La dirección puede estar en i+1 o en i+2 (si el ID se interpone)
                if (i + 1 < texts.size) {
                    if (isOrderIdNode(texts[i + 1])) {
                        // El ID se interpuso: extraer ID aquí y la dirección está en i+2
                        id = extractOrderId(texts[i + 1])
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
     * Regla de Traslado: la dirección de ENTREGA contiene "cruz verde" o coincide
     * con una dirección guardada (SavedLocationManager). La recogida no se verifica
     * porque en Traslado siempre es Cruz Verde. Única definición de la regla:
     * la usan el filtro/selectores (PicapAutoAcceptPolicy) y la card (OrderCard).
     */
    fun isTrasladoDelivery(direccionEntrega: String, context: Context): Boolean =
        direccionEntrega.contains("cruz verde", ignoreCase = true) ||
            SavedLocationManager.findMatch(direccionEntrega, context) != null

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
