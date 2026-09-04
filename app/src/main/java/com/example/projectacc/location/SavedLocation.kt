package com.example.projectacc.location

/**
 * A predefined location with coordinates and match keywords.
 * Used to override geocoding when a service origin matches.
 */
data class SavedLocation(
    val id: String = System.currentTimeMillis().toString(),
    val name: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val matches: List<String> = emptyList()
)
