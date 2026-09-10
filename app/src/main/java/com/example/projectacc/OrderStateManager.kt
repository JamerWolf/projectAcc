package com.example.projectacc

import android.content.Context
import android.content.SharedPreferences
import com.example.projectacc.model.WhatsAppService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton que gestiona el estado reactivo de la orden actual de Picap.
 * Usa StateFlow para que los composables de la UI puedan observar cambios.
 * Usa SharedPreferences para persistir configuración entre sesiones.
 */
object OrderStateManager {
    private const val PREFS_NAME = "projectacc_prefs"
    private const val KEY_VEHICLE_PLATE = "vehicle_plate"
    private const val KEY_AUTO_PLATE_ENABLED = "auto_plate_enabled"
    private const val KEY_GROUP_AUTO_RESPOND = "group_auto_respond"
    private const val KEY_SHOW_POPUP = "show_popup"
    private const val KEY_POPUP_SOUND = "popup_sound"
    private const val KEY_AUTO_ACCEPT_MIN_GANANCIA1 = "auto_accept_min_ganancia1"
    private const val KEY_AUTO_ACCEPT_MIN_GANANCIA2 = "auto_accept_min_ganancia2"
    private const val KEY_AUTO_ACCEPT_MAX_KM_COND2 = "auto_accept_max_km_cond2"
    private const val KEY_AUTO_ACCEPT_BY_KM = "auto_accept_by_km"
    private const val KEY_AUTO_ACCEPT_MAX_KM = "auto_accept_max_km"
    private const val KEY_WHATSAPP_MIN_GANANCIA1 = "whatsapp_min_ganancia1"
    private const val KEY_WHATSAPP_MIN_GANANCIA2 = "whatsapp_min_ganancia2"
    private const val KEY_WHATSAPP_MAX_KM_COND2 = "whatsapp_max_km_cond2"
    private const val KEY_WHATSAPP_AUTO_ACCEPT_BY_KM = "whatsapp_auto_accept_by_km"
    private const val KEY_WHATSAPP_MAX_KM = "whatsapp_max_km"
    private const val KEY_WHATSAPP_AUTO_ACCEPT_DELAY_ENABLED = "whatsapp_auto_accept_delay_enabled"
    private const val KEY_WHATSAPP_AUTO_ACCEPT_DELAY_MS = "whatsapp_auto_accept_delay_ms"
    private const val KEY_PICAP_AUTO_ACCEPT_ENABLED = "picap_auto_accept_enabled"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadAll()
    }

    private fun loadAll() {
        val p = prefs ?: return
        _vehiclePlate.value = p.getString(KEY_VEHICLE_PLATE, "") ?: ""
        _isAutoPlateEnabled.value = p.getBoolean(KEY_AUTO_PLATE_ENABLED, false)
        _isGroupAutoRespondEnabled.value = p.getBoolean(KEY_GROUP_AUTO_RESPOND, false)
        _isWhatsAppShowPopupEnabled.value = p.getBoolean(KEY_SHOW_POPUP, false)
        _isPopupSoundEnabled.value = p.getBoolean(KEY_POPUP_SOUND, true)
        _autoAcceptMinGanancia1.value = p.getFloat(KEY_AUTO_ACCEPT_MIN_GANANCIA1, 18000f).toDouble()
        _autoAcceptMinGanancia2.value = p.getFloat(KEY_AUTO_ACCEPT_MIN_GANANCIA2, 14000f).toDouble()
        _autoAcceptMaxKmCond2.value = p.getFloat(KEY_AUTO_ACCEPT_MAX_KM_COND2, 4.5f).toDouble()
        _isAutoAcceptByKmEnabled.value = p.getBoolean(KEY_AUTO_ACCEPT_BY_KM, false)
        _autoAcceptMaxKm.value = p.getFloat(KEY_AUTO_ACCEPT_MAX_KM, 2f).toDouble()
        _whatsappAutoAcceptMinGanancia1.value = p.getFloat(KEY_WHATSAPP_MIN_GANANCIA1, 18000f).toDouble()
        _whatsappAutoAcceptMinGanancia2.value = p.getFloat(KEY_WHATSAPP_MIN_GANANCIA2, 14000f).toDouble()
        _whatsappAutoAcceptMaxKmCond2.value = p.getFloat(KEY_WHATSAPP_MAX_KM_COND2, 4.5f).toDouble()
        _isWhatsappAutoAcceptByKmEnabled.value = p.getBoolean(KEY_WHATSAPP_AUTO_ACCEPT_BY_KM, false)
        _whatsappAutoAcceptMaxKm.value = p.getFloat(KEY_WHATSAPP_MAX_KM, 2f).toDouble()
        _isWhatsAppAutoAcceptDelayEnabled.value = p.getBoolean(KEY_WHATSAPP_AUTO_ACCEPT_DELAY_ENABLED, false)
        _whatsappAutoAcceptDelayMs.value = p.getLong(KEY_WHATSAPP_AUTO_ACCEPT_DELAY_MS, 0L)
        _isPicapAutoAcceptEnabled.value = p.getBoolean(KEY_PICAP_AUTO_ACCEPT_ENABLED, false)
    }

    private val _currentOrder = MutableStateFlow<PicapOrder?>(null)
    val currentOrder: StateFlow<PicapOrder?> = _currentOrder.asStateFlow()

    private val _isAutoClickEnabled = MutableStateFlow(false)
    val isAutoClickEnabled: StateFlow<Boolean> = _isAutoClickEnabled.asStateFlow()

    fun setOrder(order: PicapOrder) {
        _currentOrder.value = order
    }

    fun clearOrder() {
        _currentOrder.value = null
    }

    fun setAutoClickEnabled(enabled: Boolean) {
        _isAutoClickEnabled.value = enabled
    }

    private val _isNotificationClickEnabled = MutableStateFlow(false)
    val isNotificationClickEnabled: StateFlow<Boolean> = _isNotificationClickEnabled.asStateFlow()

    fun setNotificationClickEnabled(enabled: Boolean) {
        _isNotificationClickEnabled.value = enabled
    }

    // IDs de servicios ya escaneados en la lista (para evitar auto-clic en servicios viejos)
    private val _scannedServiceIds = MutableStateFlow<Set<String>>(emptySet())
    val scannedServiceIds: StateFlow<Set<String>> = _scannedServiceIds.asStateFlow()

    fun setScannedServiceIds(ids: Set<String>) {
        _scannedServiceIds.value = ids
    }

    fun addScannedServiceId(id: String) {
        _scannedServiceIds.value = _scannedServiceIds.value + id
    }

    fun clearScannedServiceIds() {
        _scannedServiceIds.value = emptySet()
    }

    private val _autoAcceptMaxKm = MutableStateFlow(2.0)
    val autoAcceptMaxKm: StateFlow<Double> = _autoAcceptMaxKm.asStateFlow()

    fun setAutoAcceptMaxKm(km: Double) {
        _autoAcceptMaxKm.value = km
        prefs?.edit()?.putFloat(KEY_AUTO_ACCEPT_MAX_KM, km.toFloat())?.apply()
    }

    private val _isAutoAcceptByKmEnabled = MutableStateFlow(false)
    val isAutoAcceptByKmEnabled: StateFlow<Boolean> = _isAutoAcceptByKmEnabled.asStateFlow()

    fun setAutoAcceptByKmEnabled(enabled: Boolean) {
        _isAutoAcceptByKmEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_AUTO_ACCEPT_BY_KM, enabled)?.apply()
    }

    // Auto-accept ganancia condition 1 (accept always)
    private val _autoAcceptMinGanancia1 = MutableStateFlow(18000.0)
    val autoAcceptMinGanancia1: StateFlow<Double> = _autoAcceptMinGanancia1.asStateFlow()

    fun setAutoAcceptMinGanancia1(ganancia: Double) {
        _autoAcceptMinGanancia1.value = ganancia
        prefs?.edit()?.putFloat(KEY_AUTO_ACCEPT_MIN_GANANCIA1, ganancia.toFloat())?.apply()
    }

    // Auto-accept ganancia condition 2 (ganancia + km)
    private val _autoAcceptMinGanancia2 = MutableStateFlow(14000.0)
    val autoAcceptMinGanancia2: StateFlow<Double> = _autoAcceptMinGanancia2.asStateFlow()

    fun setAutoAcceptMinGanancia2(ganancia: Double) {
        _autoAcceptMinGanancia2.value = ganancia
        prefs?.edit()?.putFloat(KEY_AUTO_ACCEPT_MIN_GANANCIA2, ganancia.toFloat())?.apply()
    }

    // Auto-accept max km for condition 2
    private val _autoAcceptMaxKmCond2 = MutableStateFlow(4.5)
    val autoAcceptMaxKmCond2: StateFlow<Double> = _autoAcceptMaxKmCond2.asStateFlow()

    fun setAutoAcceptMaxKmCond2(km: Double) {
        _autoAcceptMaxKmCond2.value = km
        prefs?.edit()?.putFloat(KEY_AUTO_ACCEPT_MAX_KM_COND2, km.toFloat())?.apply()
    }

    // WhatsApp orders
    private val _currentWhatsAppOrder = MutableStateFlow<WhatsAppService?>(null)
    val currentWhatsAppOrder: StateFlow<WhatsAppService?> = _currentWhatsAppOrder.asStateFlow()

    private val _isWhatsAppAutoClickEnabled = MutableStateFlow(false)
    val isWhatsAppAutoClickEnabled: StateFlow<Boolean> = _isWhatsAppAutoClickEnabled.asStateFlow()

    fun setWhatsAppOrder(order: WhatsAppService) {
        _currentWhatsAppOrder.value = order
    }

    fun clearWhatsAppOrder() {
        _currentWhatsAppOrder.value = null
    }

    fun setWhatsAppAutoClickEnabled(enabled: Boolean) {
        _isWhatsAppAutoClickEnabled.value = enabled
    }

    // WhatsApp scanned service IDs
    private val _scannedWhatsAppServiceIds = MutableStateFlow<Set<String>>(emptySet())
    val scannedWhatsAppServiceIds: StateFlow<Set<String>> = _scannedWhatsAppServiceIds.asStateFlow()

    fun setScannedWhatsAppServiceIds(ids: Set<String>) {
        _scannedWhatsAppServiceIds.value = ids
    }

    fun addScannedWhatsAppServiceId(id: String) {
        _scannedWhatsAppServiceIds.value = _scannedWhatsAppServiceIds.value + id
    }

    fun clearScannedWhatsAppServiceIds() {
        _scannedWhatsAppServiceIds.value = emptySet()
    }

    // WhatsApp plated service IDs (services where plate was already sent)
    private val _platedServiceIds = MutableStateFlow<Set<String>>(emptySet())
    val platedServiceIds: StateFlow<Set<String>> = _platedServiceIds.asStateFlow()

    fun addPlatedServiceId(id: String) {
        _platedServiceIds.value = _platedServiceIds.value + id
    }

    fun isServicePlated(id: String): Boolean {
        return _platedServiceIds.value.contains(id)
    }

    /**
     * Limpia toda la cache de servicios escaneados (Picap + WhatsApp).
     */
    fun clearAllCache() {
        _scannedServiceIds.value = emptySet()
        _scannedWhatsAppServiceIds.value = emptySet()
        _platedServiceIds.value = emptySet()
        _currentOrder.value = null
        _currentWhatsAppOrder.value = null
    }

    // Vehicle plate
    private val _vehiclePlate = MutableStateFlow("")
    val vehiclePlate: StateFlow<String> = _vehiclePlate.asStateFlow()

    fun setVehiclePlate(plate: String) {
        _vehiclePlate.value = plate
        prefs?.edit()?.putString(KEY_VEHICLE_PLATE, plate)?.apply()
    }

    // WhatsApp auto-plate (auto-paste plate on private message)
    private val _isAutoPlateEnabled = MutableStateFlow(false)
    val isAutoPlateEnabled: StateFlow<Boolean> = _isAutoPlateEnabled.asStateFlow()

    fun setAutoPlateEnabled(enabled: Boolean) {
        _isAutoPlateEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_AUTO_PLATE_ENABLED, enabled)?.apply()
    }

    // WhatsApp group auto-respond (auto-send "Me interesa {code}")
    private val _isGroupAutoRespondEnabled = MutableStateFlow(false)
    val isGroupAutoRespondEnabled: StateFlow<Boolean> = _isGroupAutoRespondEnabled.asStateFlow()

    fun setGroupAutoRespondEnabled(enabled: Boolean) {
        _isGroupAutoRespondEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_GROUP_AUTO_RESPOND, enabled)?.apply()
    }

    // WhatsApp show popup (show floating popup when service is detected)
    private val _isWhatsAppShowPopupEnabled = MutableStateFlow(false)
    val isWhatsAppShowPopupEnabled: StateFlow<Boolean> = _isWhatsAppShowPopupEnabled.asStateFlow()

    fun setWhatsAppShowPopupEnabled(enabled: Boolean) {
        _isWhatsAppShowPopupEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_SHOW_POPUP, enabled)?.apply()
    }

    // WhatsApp auto-accept settings (same conditions as Picap)
    private val _whatsappAutoAcceptMinGanancia1 = MutableStateFlow(18000.0)
    val whatsappAutoAcceptMinGanancia1: StateFlow<Double> = _whatsappAutoAcceptMinGanancia1.asStateFlow()

    fun setWhatsappAutoAcceptMinGanancia1(ganancia: Double) {
        _whatsappAutoAcceptMinGanancia1.value = ganancia
        prefs?.edit()?.putFloat(KEY_WHATSAPP_MIN_GANANCIA1, ganancia.toFloat())?.apply()
    }

    private val _whatsappAutoAcceptMinGanancia2 = MutableStateFlow(14000.0)
    val whatsappAutoAcceptMinGanancia2: StateFlow<Double> = _whatsappAutoAcceptMinGanancia2.asStateFlow()

    fun setWhatsappAutoAcceptMinGanancia2(ganancia: Double) {
        _whatsappAutoAcceptMinGanancia2.value = ganancia
        prefs?.edit()?.putFloat(KEY_WHATSAPP_MIN_GANANCIA2, ganancia.toFloat())?.apply()
    }

    private val _whatsappAutoAcceptMaxKmCond2 = MutableStateFlow(4.5)
    val whatsappAutoAcceptMaxKmCond2: StateFlow<Double> = _whatsappAutoAcceptMaxKmCond2.asStateFlow()

    fun setWhatsappAutoAcceptMaxKmCond2(km: Double) {
        _whatsappAutoAcceptMaxKmCond2.value = km
        prefs?.edit()?.putFloat(KEY_WHATSAPP_MAX_KM_COND2, km.toFloat())?.apply()
    }

    private val _isWhatsappAutoAcceptByKmEnabled = MutableStateFlow(false)
    val isWhatsappAutoAcceptByKmEnabled: StateFlow<Boolean> = _isWhatsappAutoAcceptByKmEnabled.asStateFlow()

    fun setWhatsappAutoAcceptByKmEnabled(enabled: Boolean) {
        _isWhatsappAutoAcceptByKmEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_WHATSAPP_AUTO_ACCEPT_BY_KM, enabled)?.apply()
    }

    private val _whatsappAutoAcceptMaxKm = MutableStateFlow(2.0)
    val whatsappAutoAcceptMaxKm: StateFlow<Double> = _whatsappAutoAcceptMaxKm.asStateFlow()

    fun setWhatsappAutoAcceptMaxKm(km: Double) {
        _whatsappAutoAcceptMaxKm.value = km
        prefs?.edit()?.putFloat(KEY_WHATSAPP_MAX_KM, km.toFloat())?.apply()
    }

    // Popup sound
    private val _isPopupSoundEnabled = MutableStateFlow(true)
    val isPopupSoundEnabled: StateFlow<Boolean> = _isPopupSoundEnabled.asStateFlow()

    fun setPopupSoundEnabled(enabled: Boolean) {
        _isPopupSoundEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_POPUP_SOUND, enabled)?.apply()
    }

    // WhatsApp auto-accept delay (AccessibilityService only)
    private val _isWhatsAppAutoAcceptDelayEnabled = MutableStateFlow(false)
    val isWhatsAppAutoAcceptDelayEnabled: StateFlow<Boolean> = _isWhatsAppAutoAcceptDelayEnabled.asStateFlow()

    fun setWhatsAppAutoAcceptDelayEnabled(enabled: Boolean) {
        _isWhatsAppAutoAcceptDelayEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_WHATSAPP_AUTO_ACCEPT_DELAY_ENABLED, enabled)?.apply()
    }

    private val _whatsappAutoAcceptDelayMs = MutableStateFlow(0L)
    val whatsappAutoAcceptDelayMs: StateFlow<Long> = _whatsappAutoAcceptDelayMs.asStateFlow()

    fun setWhatsAppAutoAcceptDelayMs(ms: Long) {
        _whatsappAutoAcceptDelayMs.value = ms
        prefs?.edit()?.putLong(KEY_WHATSAPP_AUTO_ACCEPT_DELAY_MS, ms)?.apply()
    }

    // Picap auto-accept switch
    private val _isPicapAutoAcceptEnabled = MutableStateFlow(false)
    val isPicapAutoAcceptEnabled: StateFlow<Boolean> = _isPicapAutoAcceptEnabled.asStateFlow()

    fun setPicapAutoAcceptEnabled(enabled: Boolean) {
        _isPicapAutoAcceptEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_PICAP_AUTO_ACCEPT_ENABLED, enabled)?.apply()
    }
}
