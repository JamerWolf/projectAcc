package com.example.projectacc

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
}
