package com.example.projectacc.picap

import android.content.Context
import android.util.Log
import com.example.projectacc.OrderStateManager
import com.example.projectacc.SERVICE_TAG
import com.example.projectacc.eval.AutoAcceptConfig
import com.example.projectacc.eval.evaluateAutoAccept
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
     *
     * El filtrado por tipo de pedido se hace únicamente por condición, mediante el
     * selector de tipo de cada condición (ver [matchesOrderType]); no existe un
     * filtro global previo.
     */
    fun shouldAutoAccept(order: PicapOrder): Boolean {
        // Chequeo de dirección Traslado diferido: se evalúa a lo sumo una vez por orden,
        // solo cuando el selector de tipo de alguna condición es TRASLADO.
        val trasladoOk = lazy { isTrasladoDeliveryOk(order) }

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
     * Lógica compartida por los selectores de tipo por condición.
     * Sistema binario: "OMS" solo si el servicio dice "Cruz Verde Integración" (con o sin
     * acento); "MOSTRADOR" es todo lo que no sea OMS. Exclusivos entre sí.
     * "TRASLADO" delega en [isTrasladoDeliveryOk] (evaluación diferida vía [trasladoOk]).
     */
    private fun matchesOrderType(order: PicapOrder, type: String, trasladoOk: Lazy<Boolean>): Boolean =
        when (type) {
            "OMS" -> PicapParser.isOmsService(order.servicio)
            "MOSTRADOR" -> !PicapParser.isOmsService(order.servicio)
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
     * Condición de dirección para el filtro Traslado: delega en la regla única
     * [PicapParser.isTrasladoDelivery] (entrega con "cruz verde" o dirección guardada).
     * Las condiciones de ganancia/km se evalúan después, aparte.
     */
    private fun isTrasladoDeliveryOk(order: PicapOrder): Boolean {
        val ok = PicapParser.isTrasladoDelivery(order.direccionEntrega, context)
        if (ok) {
            Log.d(SERVICE_TAG, "AUTO-ACCEPT: Traslado - entrega '${order.direccionEntrega}' valida (cruz verde o direccion guardada).")
        }
        return ok
    }
}
