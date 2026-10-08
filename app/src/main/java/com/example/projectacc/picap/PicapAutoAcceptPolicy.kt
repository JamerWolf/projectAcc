package com.example.projectacc.picap

import android.content.Context
import android.util.Log
import com.example.projectacc.OrderStateManager
import com.example.projectacc.SERVICE_TAG
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

        val cond1Type = OrderStateManager.picapAutoAcceptTypeCond1.value
        val cond1TypeOk = matchesOrderType(order, cond1Type, trasladoOk)
        if (cond1Type != "TODOS" && !cond1TypeOk) {
            logOrderTypeSkip(1, cond1Type, order)
        }

        // CONDICIÓN 1: Tipo de pedido coincide Y Ganancia >= umbral 1 → ACEPTAR SIEMPRE
        if (cond1TypeOk && gananciaNum >= minGanancia1) {
            Log.d(
                SERVICE_TAG,
                "AUTO-ACCEPT: Condición 1 - Ganancia $gananciaNum >= $minGanancia1. Aceptando."
            )
            return true
        }

        val cond2Type = OrderStateManager.picapAutoAcceptTypeCond2.value
        val cond2TypeOk = matchesOrderType(order, cond2Type, trasladoOk)
        if (cond2Type != "TODOS" && !cond2TypeOk) {
            logOrderTypeSkip(2, cond2Type, order)
        }

        // CONDICIÓN 2: Tipo de pedido coincide Y Ganancia >= umbral 2 Y km <= km máximo condición 2 → ACEPTAR
        if (cond2TypeOk && gananciaNum >= minGanancia2 && kmRecogida <= maxKmCond2) {
            Log.d(
                SERVICE_TAG,
                "AUTO-ACCEPT: Condición 2 - Ganancia $gananciaNum >= $minGanancia2 Y km $kmRecogida <= $maxKmCond2. Aceptando."
            )
            return true
        }

        // CONDICIÓN 3: km <= umbral seleccionado → ACEPTAR (solo si el switch está activo)
        if (OrderStateManager.isAutoAcceptByKmEnabled.value) {
            val cond3Type = OrderStateManager.picapAutoAcceptTypeCond3.value
            val cond3TypeOk = matchesOrderType(order, cond3Type, trasladoOk)
            if (cond3Type != "TODOS" && !cond3TypeOk) {
                logOrderTypeSkip(3, cond3Type, order)
            }
            if (cond3TypeOk && (autoAcceptKm >= 5.0 || kmRecogida <= autoAcceptKm)) {
                Log.d(
                    SERVICE_TAG,
                    "AUTO-ACCEPT: Condición 3 - umbral al máximo o km $kmRecogida <= umbral $autoAcceptKm. Aceptando."
                )
                return true
            }
        }

        Log.d(
            SERVICE_TAG,
            "AUTO-ACCEPT: No cumple ninguna condición (Ganancia: $gananciaNum, Km: $kmRecogida, Umbral: $autoAcceptKm)"
        )
        return false
    }

    /**
     * Decide si una orden coincide con un tipo de pedido ("OMS" | "MOSTRADOR" | "TRASLADO" | "TODOS").
     * Lógica compartida por el filtro global y los selectores de tipo por condición.
     * "TRASLADO" delega en [isTrasladoDeliveryOk] (evaluación diferida vía [trasladoOk]).
     */
    private fun matchesOrderType(order: PicapOrder, type: String, trasladoOk: Lazy<Boolean>): Boolean =
        when (type) {
            "OMS" -> !order.servicio.contains("Mostrador", ignoreCase = true)
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
