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
     * Builds the JSON object for a single location (single source of serialization).
     */
    private fun toJson(loc: SavedLocation): JSONObject {
        val obj = JSONObject()
        obj.put("id", loc.id)
        obj.put("name", loc.name)
        obj.put("lat", loc.lat)
        obj.put("lon", loc.lon)
        val matchesArray = JSONArray()
        loc.matches.forEach { matchesArray.put(it) }
        obj.put("matches", matchesArray)
        return obj
    }

    /**
     * Builds a JSON array of locations using the storage schema.
     */
    private fun toJsonArray(locations: List<SavedLocation>): JSONArray {
        val jsonArray = JSONArray()
        locations.forEach { jsonArray.put(toJson(it)) }
        return jsonArray
    }

    /**
     * Parses a single location object. Throws on any missing/invalid field.
     */
    private fun parseLocation(obj: JSONObject): SavedLocation {
        val matchesArray = obj.getJSONArray("matches")
        val matches = mutableListOf<String>()
        for (j in 0 until matchesArray.length()) {
            matches.add(matchesArray.getString(j))
        }
        return SavedLocation(
            id = obj.getString("id"),
            name = obj.getString("name"),
            lat = obj.getDouble("lat"),
            lon = obj.getDouble("lon"),
            matches = matches
        )
    }

    /**
     * Parses a JSON array of locations. Throws on any invalid element.
     */
    private fun parseArray(jsonArray: JSONArray): List<SavedLocation> {
        val locations = mutableListOf<SavedLocation>()
        for (i in 0 until jsonArray.length()) {
            locations.add(parseLocation(jsonArray.getJSONObject(i)))
        }
        return locations
    }

    /**
     * Saves a list of locations to SharedPreferences.
     */
    fun saveLocations(locations: List<SavedLocation>, context: Context) {
        val jsonArray = toJsonArray(locations)
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
            parseArray(JSONArray(json))
        } catch (e: Exception) {
            Log.e("SavedLocationManager", "Error loading: ${e.message}")
            emptyList()
        }
    }

    /**
     * Serializes current stored locations to a JSON array string (same schema as storage).
     */
    fun exportJson(context: Context): String {
        return toJsonArray(loadLocations(context)).toString()
    }

    /**
     * Parses [json], merges into stored list: same id -> updated in place,
     * new id -> appended. Returns number of locations added+updated,
     * or -1 if the JSON is not a valid location array. Stored data is left
     * untouched on parse failure, and local-only entries are never deleted.
     */
    fun importJson(json: String, context: Context): Int {
        val imported = try {
            parseArray(JSONArray(json))
        } catch (t: Throwable) {
            Log.e("SavedLocationManager", "Import parse error: ${t.message}")
            return -1
        }
        val existing = loadLocations(context).toMutableList()
        imported.forEach { loc ->
            val index = existing.indexOfFirst { it.id == loc.id }
            if (index >= 0) {
                existing[index] = loc
            } else {
                existing.add(loc)
            }
        }
        saveLocations(existing, context)
        Log.d("SavedLocationManager", "Imported ${imported.size} locations")
        return imported.size
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
