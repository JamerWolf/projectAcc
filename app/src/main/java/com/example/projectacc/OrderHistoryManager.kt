package com.example.projectacc

import android.content.Context
import android.util.Log
import com.example.projectacc.model.PicapOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Historial persistente de pedidos Picap capturados.
 *
 * Guarda TODOS los pedidos (hasta que el usuario limpie los datos de la app)
 * en SharedPreferences y los expone como StateFlow ordenados del más viejo
 * al más nuevo, para alimentar el carrusel de la pestaña Picap.
 *
 * Duplicados: un ID de pedido solo se guarda una vez.
 */
object OrderHistoryManager {
    private const val TAG = "OrderHistoryManager"
    private const val PREFS_NAME = "picap_order_history"
    private const val KEY_ORDERS = "orders"

    private val _history = MutableStateFlow<List<PicapOrder>>(emptyList())
    val history: StateFlow<List<PicapOrder>> = _history.asStateFlow()

    @Volatile
    private var initialized = false
    private var appContext: Context? = null

    private fun ensureInit(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            _history.value = load()
            initialized = true
            Log.d(TAG, "init: ${_history.value.size} pedidos en historial")
        }
    }

    /** Carga inicial (idempotente). Llamar desde MainActivity.onCreate. */
    fun init(context: Context) = ensureInit(context)

    /** Agrega un pedido si su ID aún no existe. Persiste el historial completo. */
    fun addOrder(order: PicapOrder, context: Context) {
        ensureInit(context)
        val current = _history.value
        if (current.any { it.id == order.id }) return
        val updated = current + order // oldest-first
        _history.value = updated
        persist(updated)
        Log.d(TAG, "addOrder: #${order.id} (total=${updated.size})")
    }

    /** Elimina un pedido del historial por ID. */
    fun removeById(id: String, context: Context) {
        ensureInit(context)
        val current = _history.value
        val updated = current.filterNot { it.id == id }
        if (updated.size == current.size) return
        _history.value = updated
        persist(updated)
        Log.d(TAG, "removeById: #$id (total=${updated.size})")
    }

    private fun persist(list: List<PicapOrder>) {
        val ctx = appContext ?: return
        val jsonArray = JSONArray()
        list.forEach { order ->
            val obj = JSONObject()
            obj.put("id", order.id)
            obj.put("ganancia", order.ganancia)
            obj.put("tiempoRecogida", order.tiempoRecogida)
            obj.put("direccionRecogida", order.direccionRecogida)
            obj.put("tiempoEntrega", order.tiempoEntrega)
            obj.put("direccionEntrega", order.direccionEntrega)
            obj.put("servicio", order.servicio)
            obj.put("kmRecogida", order.kmRecogida)
            obj.put("kmEntrega", order.kmEntrega)
            obj.put("timestamp", order.timestamp)
            jsonArray.put(obj)
        }
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ORDERS, jsonArray.toString()).apply()
    }

    private fun load(): List<PicapOrder> {
        val ctx = appContext ?: return emptyList()
        val json = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ORDERS, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(json)
            val orders = mutableListOf<PicapOrder>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                orders.add(
                    PicapOrder(
                        id = obj.optString("id", ""),
                        ganancia = obj.optString("ganancia", ""),
                        tiempoRecogida = obj.optString("tiempoRecogida", ""),
                        direccionRecogida = obj.optString("direccionRecogida", ""),
                        tiempoEntrega = obj.optString("tiempoEntrega", ""),
                        direccionEntrega = obj.optString("direccionEntrega", ""),
                        servicio = obj.optString("servicio", ""),
                        kmRecogida = obj.optDouble("kmRecogida", 0.0),
                        kmEntrega = obj.optDouble("kmEntrega", 0.0),
                        timestamp = obj.optLong("timestamp", 0L)
                    )
                )
            }
            orders
        } catch (e: Exception) {
            Log.e(TAG, "Error loading history: ${e.message}")
            emptyList()
        }
    }
}
