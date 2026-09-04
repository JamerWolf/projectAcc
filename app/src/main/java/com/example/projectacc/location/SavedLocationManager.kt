package com.example.projectacc.location

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages saved locations for geocoding override.
 * Stores locations in SharedPreferences.
 */
object SavedLocationManager {
    private const val PREFS_NAME = "saved_locations"
    private const val KEY_LOCATIONS = "locations"

    /**
     * Finds the best matching location for the given origin address.
     * Scoring: +2 if keyword is at the start, +1 if contained anywhere.
     */
    fun findMatch(origin: String, context: Context): SavedLocation? {
        val locations = loadLocations(context)
        val lowerOrigin = origin.lowercase().trim()

        var bestMatch: SavedLocation? = null
        var bestScore = 0

        locations.forEach { location ->
            var score = 0
            location.matches.forEach { match ->
                val lowerMatch = match.lowercase().trim()
                if (lowerOrigin.startsWith(lowerMatch)) {
                    score += 2  // Start match = 2 points
                } else if (lowerOrigin.contains(lowerMatch)) {
                    score += 1  // Contains match = 1 point
                }
            }
            if (score > bestScore) {
                bestScore = score
                bestMatch = location
            }
        }

        if (bestMatch != null) {
            Log.d("SavedLocationManager", "Best match: ${bestMatch!!.name} (score: $bestScore)")
        }

        return bestMatch
    }

    /**
     * Saves a list of locations to SharedPreferences.
     */
    fun saveLocations(locations: List<SavedLocation>, context: Context) {
        val jsonArray = JSONArray()
        locations.forEach { loc ->
            val obj = JSONObject()
            obj.put("id", loc.id)
            obj.put("name", loc.name)
            obj.put("lat", loc.lat)
            obj.put("lon", loc.lon)
            val matchesArray = JSONArray()
            loc.matches.forEach { matchesArray.put(it) }
            obj.put("matches", matchesArray)
            jsonArray.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LOCATIONS, jsonArray.toString()).apply()
        Log.d("SavedLocationManager", "Saved ${locations.size} locations")
    }

    /**
     * Loads all saved locations from SharedPreferences.
     */
    fun loadLocations(context: Context): List<SavedLocation> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LOCATIONS, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(json)
            val locations = mutableListOf<SavedLocation>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val matchesArray = obj.getJSONArray("matches")
                val matches = mutableListOf<String>()
                for (j in 0 until matchesArray.length()) {
                    matches.add(matchesArray.getString(j))
                }
                locations.add(
                    SavedLocation(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        lat = obj.getDouble("lat"),
                        lon = obj.getDouble("lon"),
                        matches = matches
                    )
                )
            }
            locations
        } catch (e: Exception) {
            Log.e("SavedLocationManager", "Error loading: ${e.message}")
            emptyList()
        }
    }

    /**
     * Adds a single location.
     */
    fun addLocation(location: SavedLocation, context: Context) {
        val locations = loadLocations(context).toMutableList()
        locations.add(location)
        saveLocations(locations, context)
    }

    /**
     * Updates an existing location by ID.
     */
    fun updateLocation(location: SavedLocation, context: Context) {
        val locations = loadLocations(context).toMutableList()
        val index = locations.indexOfFirst { it.id == location.id }
        if (index >= 0) {
            locations[index] = location
            saveLocations(locations, context)
        }
    }

    /**
     * Deletes a location by ID.
     */
    fun deleteLocation(id: String, context: Context) {
        val locations = loadLocations(context).toMutableList()
        locations.removeAll { it.id == id }
        saveLocations(locations, context)
    }
}
