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
        var minDistance: Float = getDistance(poi.lat, poi.long, city.lat, city.long)

        RegionList.cities.forEach {
            val distance = getDistance(poi.lat, poi.long, it.lat, it.long)

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
        var minDistance: Float = getDistance(location, city.lat, city.long)

        RegionList.cities.forEach {
            val distance = getDistance(location, it.lat, it.long)

            if (distance < minDistance) {
                minDistance = distance
                city = it
            }
        }

        return city to minDistance
    }

    //get distance between to places in metres
    private fun getDistance(lat1: Double, long1: Double, lat2: Double, long2: Double): Float {
        val location1 = Location("")
        location1.latitude = lat1
        location1.longitude = long1

        val location2 = Location("")
        location2.latitude = lat2
        location2.longitude = long2

        return location1.distanceTo(location2)
    }
    private fun getDistance(location1: Location, lat: Double, long: Double): Float {
        val location2 = Location("")
        location2.latitude = lat
        location2.longitude = long

        return location1.distanceTo(location2)
    }
}