package com.example.projectacc.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Helper to get current location and calculate route distance to an address.
 */
class LocationHelper(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val geocoder = Geocoder(context, Locale.getDefault())

    /**
     * Gets the current location of the user.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        return suspendCancellableCoroutine { continuation ->
            val cts = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cts.token
            ).addOnSuccessListener { location ->
                if (continuation.isActive) continuation.resume(location)
            }.addOnFailureListener { e ->
                Log.e("LocationHelper", "Error getting location: ${e.message}")
                if (continuation.isActive) continuation.resume(null)
            }
            continuation.invokeOnCancellation { cts.cancel() }
        }
    }

    /**
     * Geocodes an address string to Location coordinates.
     */
    fun geocodeAddress(address: String): Location? {
        return try {
            val results = geocoder.getFromLocationName(address, 1)
            if (!results.isNullOrEmpty()) {
                val result = results[0]
                val location = Location("")
                location.latitude = result.latitude
                location.longitude = result.longitude
                location
            } else {
                Log.w("LocationHelper", "No results for address: $address")
                null
            }
        } catch (e: Exception) {
            Log.e("LocationHelper", "Geocoding error: ${e.message}")
            null
        }
    }

    /**
     * Gets route distance in km using OSRM public API (no key needed).
     */
    suspend fun getRouteDistanceKm(
        fromLat: Double, fromLon: Double,
        toLat: Double, toLon: Double
    ): Float? {
        return withContext(Dispatchers.IO) {
            try {
                val urlStr = "https://router.project-osrm.org/route/v1/driving/" +
                    "$fromLon,$fromLat;$toLon,$toLat?overview=false"
                Log.d("LocationHelper", "OSRM URL: $urlStr")
                Log.d("LocationHelper", "From: lat=$fromLat, lon=$fromLon")
                Log.d("LocationHelper", "To: lat=$toLat, lon=$toLon")

                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("User-Agent", "projectAcc/1.0")
                conn.setRequestProperty("Accept", "application/json")

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    Log.d("LocationHelper", "OSRM response: $response")
                    val json = JSONObject(response)
                    val routes = json.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val distanceMeters = routes.getJSONObject(0).getDouble("distance")
                        Log.d("LocationHelper", "OSRM distance: ${distanceMeters/1000.0} km")
                        (distanceMeters / 1000.0).toFloat()
                    } else null
                } else {
                    val error = conn.errorStream?.bufferedReader()?.readText() ?: "unknown"
                    Log.w("LocationHelper", "OSRM error ${conn.responseCode}: $error")
                    null
                }
            } catch (e: Exception) {
                Log.e("LocationHelper", "OSRM route error: ${e.message}")
                null
            }
        }
    }

    /**
     * Gets route distance from current location to an address.
     * Returns formatted string like "3.2 km" or null if unavailable.
     */
    suspend fun getDistanceToAddress(address: String): String? {
        val currentLocation = getCurrentLocation() ?: return null
        val targetLocation = geocodeAddress(address) ?: return null

        val distanceKm = getRouteDistanceKm(
            currentLocation.latitude, currentLocation.longitude,
            targetLocation.latitude, targetLocation.longitude
        ) ?: return null

        return String.format(Locale.getDefault(), "%.1f km", distanceKm.toDouble())
    }
}
