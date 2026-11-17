package com.example.practical.network

import android.location.Location
import com.example.practical.model.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object OverpassApiService {

    suspend fun fetchNearbyPlaces(
        amenityType: String,
        radiusMeters: Int,
        userLat: Double,
        userLon: Double
    ): List<Place> = withContext(Dispatchers.IO) {
        val query = "[out:json];node[amenity=$amenityType](around:$radiusMeters,$userLat,$userLon);out;"
        val urlString = "https://overpass-api.de/api/interpreter?data=${URLEncoder.encode(query, "UTF-8")}"

        val places = mutableListOf<Place>()
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("User-Agent", "EmergencyServicesApp/1.0")

        try {
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val responseText = inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(responseText)
                val elements = jsonObject.optJSONArray("elements")

                if (elements != null) {
                    for (i in 0 until elements.length()) {
                        val element = elements.getJSONObject(i)
                        val id = element.getLong("id")
                        val lat = element.getDouble("lat")
                        val lon = element.getDouble("lon")

                        val tags = element.optJSONObject("tags")
                        val rawName = tags?.optString("name")
                        val name = if (!rawName.isNullOrBlank()) {
                            rawName
                        } else {
                            val categoryCap = amenityType.replaceFirstChar { it.uppercase() }
                            "$categoryCap #$id"
                        }

                        val phone = tags?.let {
                            val p1 = it.optString("phone")
                            if (p1.isNotBlank()) return@let p1
                            val p2 = it.optString("contact:phone")
                            if (p2.isNotBlank()) return@let p2
                            val p3 = it.optString("phone:mobile")
                            if (p3.isNotBlank()) return@let p3
                            null
                        }

                        // Compute distance with Location.distanceBetween() as required
                        val distanceResults = FloatArray(1)
                        Location.distanceBetween(userLat, userLon, lat, lon, distanceResults)
                        val distanceMeters = distanceResults[0]

                        places.add(
                            Place(
                                id = id,
                                name = name,
                                amenity = amenityType,
                                lat = lat,
                                lon = lon,
                                phone = phone,
                                distanceMeters = distanceMeters
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection.disconnect()
        }

        // Sort nearest first as required
        places.sortBy { it.distanceMeters }
        return@withContext places
    }
}
