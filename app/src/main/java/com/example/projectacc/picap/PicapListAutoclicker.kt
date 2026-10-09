package com.example.projectacc.picap

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.projectacc.OrderStateManager
import com.example.projectacc.SERVICE_TAG

/**
 * List autoclick for Picap: detects "Precio estimado", skips already-scanned
 * IDs (memory in [OrderStateManager]) and clicks the first new service.
 * Reads OrderStateManager globals directly.
 */
@Suppress("DEPRECATION")
class PicapListAutoclicker {

    // Controla si ya se escaneó la lista al iniciar el auto-clic
    internal var hasScannedInitially = false

    /**
     * Busca recursivamente en el árbol de nodos un elemento cuyo contentDescription
     * contenga "Precio estimado" (orden en lista de Picap) y hace clic en él.
     * Intenta primero clic directo en el nodo; si no es cliqueable, intenta con el padre.
     * Evita hacer clic en servicios ya escaneados (memoria de IDs).
     *
     * @return true si se encontró y se hizo clic exitosamente.
     */
    fun findAndClickEstimatedPrice(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        // --- ESCANEAR LISTA AL INICIAR (una sola vez cuando se activa el switch) ---
        if (!hasScannedInitially) {
            val currentIds = mutableSetOf<String>()
            extractAllServiceIds(node, currentIds)
            if (currentIds.isNotEmpty()) {
                OrderStateManager.setScannedServiceIds(currentIds)
                Log.d(SERVICE_TAG, "MEMORY: IDs guardados al iniciar: $currentIds")
            }
            hasScannedInitially = true
        }

        val cd = node.contentDescription?.toString() ?: ""
        if (cd.contains("Precio estimado")) {
            // Extraer el ID de este servicio
            val idMatch = Regex("ID: ([a-f0-9]+)", RegexOption.IGNORE_CASE).find(cd)
            val serviceId = idMatch?.groupValues?.get(1) ?: ""

            // --- VERIFICAR MEMORIA ANTES DE HACER CLIC ---
            val scannedIds = OrderStateManager.scannedServiceIds.value
            if (serviceId.isNotEmpty() && scannedIds.contains(serviceId)) {
                Log.d(SERVICE_TAG, "MEMORY: ID $serviceId ya está en memoria (servicio viejo). Ignorando.")
                // No hacer clic, continuar buscando en otros nodos
            } else {
                // ID nuevo o no se pudo extraer: hacer clic
                Log.d(SERVICE_TAG, "AUTOCLICK: Orden nueva detectada (ID: $serviceId). Intentando clic...")

                if (node.isClickable) {
                    val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (result) {
                        // Añadir a memoria después del clic exitoso
                        if (serviceId.isNotEmpty()) {
                            OrderStateManager.addScannedServiceId(serviceId)
                            Log.d(SERVICE_TAG, "MEMORY: ID $serviceId añadido a memoria tras clic exitoso.")
                        }
                        return true
                    }
                }

                // Intentar con el padre
                val parent = node.parent
                if (parent != null) {
                    if (parent.isClickable) {
                        val result = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (result) {
                            if (serviceId.isNotEmpty()) {
                                OrderStateManager.addScannedServiceId(serviceId)
                            }
                            parent.recycle()
                            return true
                        }
                    }
                    parent.recycle()
                }
            }
        }

        // Buscar recursivamente en hijos
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val found = findAndClickEstimatedPrice(child)
                child.recycle()
                if (found) return true
            }
        }
        return false
    }

    /**
     * Extrae todos los IDs de servicios de la lista actual de Picap.
     * Busca nodos con "Precio estimado" y extrae el ID del contentDescription.
     * Se usa al activar el switch para guardar la lista actual.
     */
    private fun extractAllServiceIds(node: AccessibilityNodeInfo?, ids: MutableSet<String>) {
        if (node == null) return

        val cd = node.contentDescription?.toString() ?: ""
        if (cd.contains("Precio estimado")) {
            // Buscar el patrón "ID: XXXXX" en el contentDescription
            val idMatch = Regex("ID: ([a-f0-9]+)", RegexOption.IGNORE_CASE).find(cd)
            if (idMatch != null) {
                val id = idMatch.groupValues[1]
                ids.add(id)
                Log.d(SERVICE_TAG, "MEMORY: Guardando ID de lista: $id")
            }
        }

        // Continuar buscando en hijos
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractAllServiceIds(child, ids)
                child.recycle()
            }
        }
    }
}
