package com.adriantache.poitracker.utils

import com.adriantache.poitracker.data.RegionList
import com.adriantache.poitracker.models.City
import com.adriantache.poitracker.models.POI

/**
 * Utility class for various methods
 */

object Utils {
    //get closest city to POI
    /**
     * making assumptions:
     * city list is not empty
     * all POIs are inside a city
     */
    fun getCity(poi: POI): Pair<City, Float> {
        var city: City = RegionList.cities[0]
        var minDistance: Float = poi.location.distanceTo(city.location)

        RegionList.cities.forEach{
            val distance = poi.location.distanceTo(it.location)

            if(distance < minDistance) {
                minDistance = distance
                city = it
            }
        }

        return city to minDistance
    }
}