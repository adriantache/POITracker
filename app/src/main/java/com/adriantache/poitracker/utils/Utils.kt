package com.adriantache.poitracker.utils

import android.location.Location
import com.adriantache.poitracker.data.RegionList
import com.adriantache.poitracker.models.City
import com.adriantache.poitracker.models.POI


/**
 * Utility class for various methods
 */

object Utils {
    //get closest city to POI and distance
    fun getCity(poi: POI): Pair<City, Float> {
        var city: City = RegionList.cities[0]
        var minDistance: Float = poi.location.distanceTo(city.location)

        RegionList.cities.forEach {
            val distance = poi.location.distanceTo(it.location)

            if (distance < minDistance) {
                minDistance = distance
                city = it
            }
        }

        return city to minDistance
    }

    //get closest city to user location
    fun getCity(location: Location): Pair<City, Float> {
        var city: City = RegionList.cities[0]
        var minDistance: Float = location.distanceTo(city.location)

        RegionList.cities.forEach {
            val distance = location.distanceTo(it.location)

            if (distance < minDistance) {
                minDistance = distance
                city = it
            }
        }

        return city to minDistance
    }
}