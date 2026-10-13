package com.example.projectacc.picap

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del matcher exacto de la etiqueta del botón Aceptar. El match por
 * subestringa era letal: el banner de filtro de Picap "No cumple tus filtros
 * de autoaceptar" contiene "aceptar" y va antes que el botón en pre-orden,
 * robándole los bounds del tap (regresión #db527).
 */
class AcceptButtonLabelTest {

    @Test
    fun `etiqueta exacta Aceptar matchea`() {
        assertTrue(isAcceptButtonLabel("Aceptar", null))
    }

    @Test
    fun `etiqueta con espacios y case variados matchea por trim e ignoreCase`() {
        assertTrue(isAcceptButtonLabel("  Aceptar ", null))
        assertTrue(isAcceptButtonLabel("ACEPTAR", null))
        assertTrue(isAcceptButtonLabel("", "Aceptar"))
        assertTrue(isAcceptButtonLabel("", "aceptar"))
    }

    @Test
    fun `banner de filtros de autoaceptar no matchea`() {
        val banner = "No cumple tus filtros de autoaceptar"
        assertFalse(isAcceptButtonLabel(banner, null))
        assertFalse(isAcceptButtonLabel("", banner))
    }

    @Test
    fun `labels que solo contienen la palabra no matchean`() {
        assertFalse(isAcceptButtonLabel("Aceptar oferta", null))
        assertFalse(isAcceptButtonLabel("", "Rechazar / Aceptar"))
        assertFalse(isAcceptButtonLabel("", null))
    }
}
