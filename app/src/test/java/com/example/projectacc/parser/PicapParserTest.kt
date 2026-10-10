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
}
