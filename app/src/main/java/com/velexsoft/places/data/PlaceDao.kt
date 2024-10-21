package com.velexsoft.places.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {

    @Query("SELECT * FROM places ORDER BY name")
    fun getPlaces(): LiveData<List<Place>>

    @Query("SELECT * from places ORDER BY name")
    fun getPlacesFlow(): Flow<List<Place>>

    @Query("SELECT * FROM places WHERE countryCode = :countryCode ORDER BY name")
    fun getPlacesWithGrowZoneNumber(countryCode: String): LiveData<List<Place>>

    @Query("SELECT * from places WHERE countryCode = :countryCode ORDER BY name")
    fun getPlacesWithCountryCodeFlow(countryCode: String): Flow<List<Place>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(places: List<Place>)

}