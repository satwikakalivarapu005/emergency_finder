package com.example.practical.model

import java.util.Locale

data class Place(
    val id: Long,
    val name: String,
    val amenity: String,
    val lat: Double,
    val lon: Double,
    val phone: String?,
    var distanceMeters: Float = 0f,
    var isFavourite: Boolean = false
) {
    fun getFormattedDistance(): String {
        return if (distanceMeters < 1000) {
            "${distanceMeters.toInt()} m"
        } else {
            String.format(Locale.getDefault(), "%.1f km", distanceMeters / 1000f)
        }
    }
}
