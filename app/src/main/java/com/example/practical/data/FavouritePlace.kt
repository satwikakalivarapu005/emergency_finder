package com.example.practical.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favourite_places")
data class FavouritePlace(
    @PrimaryKey val id: Long,
    val name: String,
    val amenity: String,
    val lat: Double,
    val lon: Double,
    val phone: String?
)
