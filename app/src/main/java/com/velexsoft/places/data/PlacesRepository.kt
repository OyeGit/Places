package com.velexsoft.places.data

import androidx.annotation.AnyThread
import androidx.lifecycle.LiveData
import com.velexsoft.places.data.network.NetworkService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import androidx.lifecycle.liveData
import androidx.lifecycle.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.switchMap
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext


@ExperimentalCoroutinesApi
@FlowPreview
class PlacesRepository private constructor (
    private val placeDao: PlaceDao,
    private val placeService: NetworkService,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    ){

    // Cache for storing the custom sort order
    private var placeListSortOrderCache = CacheOnSuccess( onErrorFallback =
    { listOf<String>() }) {
        placeService.customPlaceSortOrder()
    }

    val places: LiveData<List<Place>> = liveData<List<Place>> {

        val placesLiveData = placeDao.getPlaces()

        val customSortOrder = placeListSortOrderCache.getOrAwait()

        emitSource( placesLiveData.map {
            placeList -> placeList.applySort( customSortOrder )
            }
        )
    }

    fun getPlacesWithCountryCode(code: CountryCode ) =
        placeDao.getPlacesWithCountryCodeFlow(code.toString())
            // Apply switchMap, which "switches" to a new liveData every time a new value is
            // received
            .mapLatest { placeList ->
                // Use the liveData builder to construct a new coroutine-backed LiveData
                liveData {
                    val customSortOrder = placeListSortOrderCache.getOrAwait()
                    // Emit the sorted list to the LiveData builder, which will be the new value
                    // sent to getPlantsWithGrowZoneNumber
                    emit(placeList.applyMainSafeSort(customSortOrder))
                }
            }

    private val customSortFlow = placeListSortOrderCache::getOrAwait.asFlow()

    val placesFlow: Flow<List<Place>>
        get() = placeDao.getPlacesFlow()
            // When the result of customSortFlow is available, this will combine it with the latest
            // value from the flow above.  Thus, as long as both `plants` and `sortOrder`
            // have an initial value (their flow has emitted at least one value), any change
            // to either `plants` or `sortOrder` will call `plants.applySort(sortOrder)`.
            .combine(customSortFlow) { places, sortOrder ->
                places.applySort(sortOrder)
            }
            // Flow allows you to switch the dispatcher the previous transforms run on.
            // Doing so introduces a buffer that the lines above this can write to, which we don't
            // need for this UI use-case that only cares about the latest value.
            //
            // This flowOn is needed to make the [background-thread only] applySort call above
            // run on a background thread.
            .flowOn(defaultDispatcher)

            // We can tell flow to make the buffer "conflated". It removes the buffer from flowOn
            // and only shares the last value, as our UI discards any intermediate values in the
            // buffer.
            .conflate()




    fun getPlantsWithGrowZoneFlow(code: CountryCode): Flow<List<Place>> {
        // A Flow from Room will return each value, just like a LiveData.
        return placeDao.getPlacesWithCountryCodeFlow( code.toString() )
            // When a new value is sent from the database, we can transform it using a
            // suspending map function. This allows us to call async code like here
            // where it potentially loads the sort order from network (if not cached)
            //
            // Since all calls in this map are main-safe, flowOn is not needed here.
            // Both Room and Retrofit will run the query on a different thread even though this
            // flow is using the main thread.
            .map { placeList ->

                // We can make a request for the cached sort order directly here, because map
                // takes a suspend lambda
                //
                // This may trigger a network request if it's not yet cached, but since the network
                // call is main safe, we won't block the main thread (even though this flow executes
                // on Dispatchers.Main).
                val sortOrderFromNetwork = placeListSortOrderCache.getOrAwait()

                // The result will be the sorted list with custom sort order applied. Note that this
                // call is also main-safe due to using applyMainSafeSort.
                val nextValue = placeList.applyMainSafeSort(sortOrderFromNetwork)
                nextValue
            }
    }


    private fun List<Place>.applySort(customSortOrder: List<String>): List<Place> {
        // Our product manager requested that these plants always be sorted first in this
        // order whenever they are present in the array
        return sortedBy { place ->
            val positionForItem = customSortOrder.indexOf(place.placeId).let { order ->
                if (order > -1) order else Int.MAX_VALUE
            }
            ComparablePair(positionForItem, place.name)
        }
    }

    @AnyThread
    private suspend fun List<Place>.applyMainSafeSort(customSortOrder: List<String>) =
        withContext(defaultDispatcher) {
            this@applyMainSafeSort.applySort(customSortOrder)
        }

    /**
     * Returns true if we should make a network request.
     */
    private suspend fun shouldUpdatePlacesCache(code:CountryCode): Boolean {
        // suspending function, so you can e.g. check the status of the database here
        return true
    }

    suspend fun tryUpdateRecentPlacesCache() {
        if (shouldUpdatePlacesCache(NoCountryCode)) fetchRecentPlaces()
    }

    suspend fun tryUpdateRecentPlantsForGrowZoneCache(code: CountryCode ) {
        if (shouldUpdatePlacesCache(code)) fetchPlantsForGrowZone(code)
    }

    /**
     * Fetch a new list of plants from the network, and append them to [plantDao]
     */
    private suspend fun fetchRecentPlaces() {
        val plants = placeService.allPlants()
        placeDao.insertAll(plants)
    }

    /**
     * Fetch a list of plants for a grow zone from the network, and append them to [plantDao]
     */
    private suspend fun fetchPlantsForGrowZone( code: CountryCode ): List<Place> {
        val plants = placeService.placesByGrowZone(code)
        placeDao.insertAll(plants)
        return plants
    }

    companion object {
        // For Singleton instantiation
        @Volatile private var instance: PlacesRepository? = null

        fun getInstance(plantDao: PlaceDao, plantService: NetworkService) =
            instance ?: synchronized(this) {
                instance ?: PlacesRepository(plantDao, plantService).also { instance = it }
            }
    }
}