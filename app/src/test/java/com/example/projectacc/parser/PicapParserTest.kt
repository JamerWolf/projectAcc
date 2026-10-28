package com.example.projectacc.parser

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests unitarios de [PicapParser.parseOrder] sobre los textos del árbol de la oferta.
 */
class PicapParserTest {

    @Test
    fun `parsea entrega clasica con id interpuesto`() {
        val texts = listOf(
            "Cruz verde Mostrador",
            "Tu ganancia final",
            "$ 12.000",
            "A 9 mins (4.31 km)",
            "Calle 10 # 5-20",
            "12 min (3.4 km)",
            "ID: 555",
            "Carrera 7 # 3-10"
        )

        val order = PicapParser.parseOrder(texts)

        assertEquals("555", order.id)
        assertEquals("Calle 10 # 5-20", order.direccionRecogida)
        assertEquals("Carrera 7 # 3-10", order.direccionEntrega)
        assertEquals(4.31, order.kmRecogida, 0.001)
        assertEquals(3.4, order.kmEntrega, 0.001)
        assertEquals("Cruz verde Mostrador", order.servicio)
    }

    @Test
    fun `parsea entrega en forma corta menos de un minuto`() {
        val texts = listOf(
            "Cruz verde Mostrador",
            "Tu ganancia final",
            "$ 8.500",
            "A 3 mins (1.2 km)",
            "Av 6 # 12-34",
            "A menos de un minuto (890m)",
            "ID: 777",
            "Calle 1 # 2-3"
        )

        val order = PicapParser.parseOrder(texts)

        // La forma corta no debe pisar la recogida ni dejar la entrega sin parsear.
        assertEquals("A 3 mins (1.2 km)", order.tiempoRecogida)
        assertEquals("Av 6 # 12-34", order.direccionRecogida)
        assertEquals("A menos de un minuto (890m)", order.tiempoEntrega)
        assertEquals("Calle 1 # 2-3", order.direccionEntrega)
        assertEquals("777", order.id)
        assertEquals(1.2, order.kmRecogida, 0.001)
        assertEquals(0.89, order.kmEntrega, 0.001)
    }

    @Test
    fun `primera linea con forma A sigue siendo la recogida`() {
        val texts = listOf(
            "A menos de un minuto (450m)",
            "Calle 5 # 1-1",
            "8 min (2.0 km)",
            "Calle 9 # 2-3"
        )

        val order = PicapParser.parseOrder(texts)

        assertEquals("A menos de un minuto (450m)", order.tiempoRecogida)
        assertEquals("Calle 5 # 1-1", order.direccionRecogida)
        assertEquals("8 min (2.0 km)", order.tiempoEntrega)
        assertEquals("Calle 9 # 2-3", order.direccionEntrega)
        assertEquals(0.45, order.kmRecogida, 0.001)
        assertEquals(2.0, order.kmEntrega, 0.001)
    }

    @Test
    fun `parsea popup 2026-11 con ID sin dos puntos y prefijos Recoger Entregar`() {
        // Textos tal como los muestra el popup nuevo (captura de campo).
        val texts = listOf(
            "Mensajería",
            "ID c8f0f",
            "Tu ganancia final",
            "16.001 COP",
            "1.060 COP/Km",
            "Usuario corp. verificado",
            "Cruz Verde Colombia",
            "No cumple tus filtros de autoaceptar",
            "Recoger · a 11 mins (4.76 km)",
            "Calle 15 & Avenida 0, Cúcuta, Norte de Santander, Colombia",
            "Entregar · 11 min (15.1 km)",
            "Calle 14a # 20-45, Barrio Toledo Plata, Cúcuta, Norte de Santander, Colombia",
            "5.0",
            "Hombre",
            "5042 Viajes",
            "Aceptar"
        )

        val order = PicapParser.parseOrder(texts)

        assertEquals("c8f0f", order.id)
        assertEquals("16.001 COP", order.ganancia)
        assertEquals("Recoger · a 11 mins (4.76 km)", order.tiempoRecogida)
        assertEquals("Calle 15 & Avenida 0, Cúcuta, Norte de Santander, Colombia", order.direccionRecogida)
        assertEquals("Entregar · 11 min (15.1 km)", order.tiempoEntrega)
        assertEquals(
            "Calle 14a # 20-45, Barrio Toledo Plata, Cúcuta, Norte de Santander, Colombia",
            order.direccionEntrega
        )
        assertEquals("Cruz Verde Colombia", order.servicio)
        assertEquals(4.76, order.kmRecogida, 0.001)
        assertEquals(15.1, order.kmEntrega, 0.001)
    }

    @Test
    fun `extrae el ID del popup con y sin dos puntos`() {
        assertEquals("c8f0f", PicapParser.extractOrderId("ID c8f0f"))
        assertEquals("555", PicapParser.extractOrderId("ID: 555"))
        assertEquals("c8f0f", PicapParser.extractOrderId("Mensajería ID c8f0f"))
        assertEquals("", PicapParser.extractOrderId("Aceptar"))
    }

    @Test
    fun `entrega corta sin prefijo A con metros decimales (Picap 2026-10)`() {
        val texts = listOf(
            "Tu ganancia final",
            "4.658 COP",
            "Cruz Verde Mostrador",
            "Usuario frecuente",
            "ID: eed80",
            "A 9 mins (3.79 km)",
            "Parroquia San Luis Gonzaga, Cucuta",
            "Menos de un minuto (259.9 m)",
            "Conjunto Los Mangos, Cucuta"
        )

        val order = PicapParser.parseOrder(texts)

        // El nuevo popup ya no escribe "A " ni "min (" en la entrega corta.
        assertEquals("eed80", order.id)
        assertEquals("A 9 mins (3.79 km)", order.tiempoRecogida)
        assertEquals("Parroquia San Luis Gonzaga, Cucuta", order.direccionRecogida)
        assertEquals("Menos de un minuto (259.9 m)", order.tiempoEntrega)
        assertEquals("Conjunto Los Mangos, Cucuta", order.direccionEntrega)
        assertEquals(3.79, order.kmRecogida, 0.001)
        assertEquals(0.2599, order.kmEntrega, 0.001)
        assertEquals("Cruz Verde Mostrador", order.servicio)
    }
}
