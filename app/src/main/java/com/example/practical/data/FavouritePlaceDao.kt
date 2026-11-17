package com.example.practical.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavouritePlaceDao {
    @Query("SELECT * FROM favourite_places")
    fun getAllFavourites(): Flow<List<FavouritePlace>>

    @Query("SELECT * FROM favourite_places")
    suspend fun getFavouritesList(): List<FavouritePlace>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavourite(place: FavouritePlace)

    @Delete
    suspend fun deleteFavourite(place: FavouritePlace)

    @Query("DELETE FROM favourite_places WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM favourite_places WHERE id = :id)")
    suspend fun isFavourite(id: Long): Boolean
}
