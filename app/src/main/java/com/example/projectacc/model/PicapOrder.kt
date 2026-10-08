package com.example.projectacc.model

/**
 * Modelo de datos para representar una orden de Picap capturada.
 */
data class PicapOrder(
    val id: String = "",
    val ganancia: String = "",
    val tiempoRecogida: String = "",
    val direccionRecogida: String = "",
    val tiempoEntrega: String = "",
    val direccionEntrega: String = "",
    val servicio: String = "",
    val kmRecogida: Double = 0.0,
    val kmEntrega: Double = 0.0,
    val timestamp: Long = 0L
)
