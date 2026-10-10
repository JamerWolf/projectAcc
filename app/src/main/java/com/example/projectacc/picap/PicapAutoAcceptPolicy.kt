package com.example.projectacc.picap

import android.content.Context
import android.util.Log
import com.example.projectacc.OrderStateManager
import com.example.projectacc.SERVICE_TAG
import com.example.projectacc.eval.AutoAcceptConfig
import com.example.projectacc.eval.evaluateAutoAccept
import com.example.projectacc.location.SavedLocationManager
import com.example.projectacc.model.PicapOrder
import com.example.projectacc.parser.PicapParser

/**
 * Decision rules for Picap auto-accept. Reads [OrderStateManager] globals
 * directly (they are already singletons); no state is abstracted or copied.
 *
 * @param context used by [SavedLocationManager.findMatch] for saved addresses.
 */
class PicapAutoAcceptPolicy(private val context: Context) {

    /**
     * Decide si una orden debe ser auto-aceptada según las reglas:
     * 1. Si ganancia >= 18000 COP → ACEPTAR SIEMPRE (sin importar km)
     * 2. Si ganancia >= 14000 COP Y km de recogida <= 4.5 → ACEPTAR
     * 3. Si km <= umbral seleccionado por el usuario → ACEPTAR (sin importar precio)
     */
    fun shouldAutoAccept(order: PicapOrder): Boolean {
        // Chequeo de dirección Traslado diferido: se evalúa a lo sumo una vez por orden,
        // tanto para el filtro global como para los selectores por condición.
        val trasladoOk = lazy { isTrasladoDeliveryOk(order) }

        // Filtro de tipo de pedido (selector en la pestaña Picap): OMS / Mostrador / Traslado / Todos.
        // Traslado (Picap lo muestra como Mostrador) solo pasa si la ENTREGA es Cruz Verde
        // o una dirección guardada; TODOS acepta todo sin mirar dirección.
        val serviceFilter = OrderStateManager.picapAutoAcceptFilter.value
        when (serviceFilter) {
            "OMS" -> if (!matchesOrderType(order, "OMS", trasladoOk)) {
                Log.d(SERVICE_TAG, "AUTO-ACCEPT: Filtro OMS excluye servicio '${order.servicio}'. No aceptando.")
                return false
            }
            "MOSTRADOR" -> if (!matchesOrderType(order, "MOSTRADOR", trasladoOk)) {
                Log.d(SERVICE_TAG, "AUTO-ACCEPT: Filtro Mostrador excluye servicio '${order.servicio}'. No aceptando.")
                return false
            }
            "TRASLADO" -> if (!matchesOrderType(order, "TRASLADO", trasladoOk)) {
                Log.d(
                    SERVICE_TAG,
                    "AUTO-ACCEPT: Filtro Traslado - entrega '${order.direccionEntrega}' no es Cruz Verde ni direccion guardada. No aceptando."
                )
                return false
            }
            // "TODOS" u otro valor: acepta OMS, Mostrador y Traslado sin filtrar direccion
        }

        val gananciaNum = order.ganancia
            .replace("COP", "")
            .replace(" ", "")
            .replace(".", "")
            .toIntOrNull() ?: 0

        val kmRecogida = PicapParser.extractKmFromPickup(order.tiempoRecogida)
        val autoAcceptKm = OrderStateManager.autoAcceptMaxKm.value
        val minGanancia1 = OrderStateManager.autoAcceptMinGanancia1.value.toInt()
        val minGanancia2 = OrderStateManager.autoAcceptMinGanancia2.value.toInt()
        val maxKmCond2 = OrderStateManager.autoAcceptMaxKmCond2.value

        val config = AutoAcceptConfig(
            minGanancia1,
            minGanancia2,
            maxKmCond2,
            OrderStateManager.isAutoAcceptByKmEnabled.value,
            autoAcceptKm
        )
        // Gates por tipo de pedido evaluados DENTRO del lambda: el evaluator los consulta
        // en el mismo orden secuencial que el código original (cond N solo se evalúa si
        // la N-1 no aceptó), por lo que logOrderTypeSkip conserva su posición exacta.
        val result = evaluateAutoAccept(gananciaNum, kmRecogida, config) { n ->
            val type = when (n) {
                1 -> OrderStateManager.picapAutoAcceptTypeCond1.value
                2 -> OrderStateManager.picapAutoAcceptTypeCond2.value
                else -> OrderStateManager.picapAutoAcceptTypeCond3.value
            }
            val ok = matchesOrderType(order, type, trasladoOk)
            if (type != "TODOS" && !ok) {
                logOrderTypeSkip(n, type, order)
            }
            ok
        }

        when (result.condition) {
            1 -> Log.d(
                SERVICE_TAG,
                "AUTO-ACCEPT: Condición 1 - Ganancia $gananciaNum >= $minGanancia1. Aceptando."
            )
            2 -> Log.d(
                SERVICE_TAG,
                "AUTO-ACCEPT: Condición 2 - Ganancia $gananciaNum >= $minGanancia2 Y km $kmRecogida <= $maxKmCond2. Aceptando."
            )
            3 -> Log.d(
                SERVICE_TAG,
                "AUTO-ACCEPT: Condición 3 - umbral al máximo o km $kmRecogida <= umbral $autoAcceptKm. Aceptando."
            )
            else -> Log.d(
                SERVICE_TAG,
                "AUTO-ACCEPT: No cumple ninguna condición (Ganancia: $gananciaNum, Km: $kmRecogida, Umbral: $autoAcceptKm)"
            )
        }
        return result.accepted
    }

    /**
     * Decide si una orden coincide con un tipo de pedido ("OMS" | "MOSTRADOR" | "TRASLADO" | "TODOS").
     * Lógica compartida por el filtro global y los selectores de tipo por condición.
     * "OMS" solo si el servicio dice "Cruz Verde Integración" (con o sin acento).
     * "TRASLADO" delega en [isTrasladoDeliveryOk] (evaluación diferida vía [trasladoOk]).
     */
    private fun matchesOrderType(order: PicapOrder, type: String, trasladoOk: Lazy<Boolean>): Boolean =
        when (type) {
            "OMS" -> PicapParser.isOmsService(order.servicio)
            "MOSTRADOR" -> order.servicio.contains("Mostrador", ignoreCase = true)
            "TRASLADO" -> trasladoOk.value
            // "TODOS" u otro valor: coincide con todo sin filtrar direccion
            else -> true
        }

    /**
     * Log de omisión cuando el selector de tipo de pedido de una condición excluye la orden.
     */
    private fun logOrderTypeSkip(condition: Int, type: String, order: PicapOrder) {
        val detail =
            if (type == "TRASLADO") "entrega '${order.direccionEntrega}'" else "servicio '${order.servicio}'"
        Log.d(
            SERVICE_TAG,
            "AUTO-ACCEPT: Condición $condition - tipo de pedido (selector=$type) no coincide con $detail. Condición omitida."
        )
    }

    /**
     * Condición de dirección para el filtro Traslado: la dirección de ENTREGA debe
     * contener "cruz verde" o coincidir con una dirección guardada (SavedLocationManager).
     * La recogida NO se verifica (siempre es Cruz Verde en Traslado).
     * Las condiciones de ganancia/km se evalúan después, aparte.
     */
    private fun isTrasladoDeliveryOk(order: PicapOrder): Boolean {
        if (order.direccionEntrega.contains("cruz verde", ignoreCase = true)) {
            Log.d(SERVICE_TAG, "AUTO-ACCEPT: Traslado - entrega contiene 'cruz verde'. Direccion OK.")
            return true
        }
        val match = SavedLocationManager.findMatch(order.direccionEntrega, context)
        if (match != null) {
            Log.d(SERVICE_TAG, "AUTO-ACCEPT: Traslado - entrega coincide con direccion guardada '${match.name}'.")
            return true
        }
        return false
    }
}
