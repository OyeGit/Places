package com.velexsoft.places.data.network

import com.velexsoft.places.data.CountryCode
import com.velexsoft.places.data.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

class NetworkService {

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://raw.githubusercontent.com/")
        .client(OkHttpClient())
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val placeService = retrofit.create( PlantServices::class.java )

    suspend fun allPlants(): List<Place> = withContext(Dispatchers.Default) {
        delay(1500)
        val result = placeService.getAllPlaces()
        result.shuffled()
    }

    suspend fun placesByGrowZone(code: CountryCode) = withContext(Dispatchers.Default) {
        delay(1500)
        val result = placeService.getAllPlaces()
        result.filter { it.countryCode.equals(code) }.shuffled()
    }

    suspend fun customPlaceSortOrder(): List<String> = withContext(Dispatchers.Default) {
        val result = placeService.getCustomPlacesSortOrder()
        result.map { place -> place.placeId }
    }
}

interface PlantServices {
    @GET("googlecodelabs/kotlin-coroutines/master/advanced-coroutines-codelab/sunflower/src/main/assets/plants.json")
    suspend fun getAllPlaces() : List<Place>

    @GET("googlecodelabs/kotlin-coroutines/master/advanced-coroutines-codelab/sunflower/src/main/assets/custom_plant_sort_order.json")
    suspend fun getCustomPlacesSortOrder() : List<Place>
}