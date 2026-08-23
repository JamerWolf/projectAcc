package com.example.projectacc

import com.example.projectacc.model.WhatsAppService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton que gestiona el estado reactivo de la orden actual de Picap.
 * Usa StateFlow para que los composables de la UI puedan observar cambios.
 */
object OrderStateManager {
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
    }

    private val _isAutoAcceptByKmEnabled = MutableStateFlow(false)
    val isAutoAcceptByKmEnabled: StateFlow<Boolean> = _isAutoAcceptByKmEnabled.asStateFlow()

    fun setAutoAcceptByKmEnabled(enabled: Boolean) {
        _isAutoAcceptByKmEnabled.value = enabled
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
    }

    // WhatsApp auto-plate (auto-paste plate on private message)
    private val _isAutoPlateEnabled = MutableStateFlow(false)
    val isAutoPlateEnabled: StateFlow<Boolean> = _isAutoPlateEnabled.asStateFlow()

    fun setAutoPlateEnabled(enabled: Boolean) {
        _isAutoPlateEnabled.value = enabled
    }

    // WhatsApp group auto-respond (auto-send "Me interesa {code}")
    private val _isGroupAutoRespondEnabled = MutableStateFlow(false)
    val isGroupAutoRespondEnabled: StateFlow<Boolean> = _isGroupAutoRespondEnabled.asStateFlow()

    fun setGroupAutoRespondEnabled(enabled: Boolean) {
        _isGroupAutoRespondEnabled.value = enabled
    }
}
