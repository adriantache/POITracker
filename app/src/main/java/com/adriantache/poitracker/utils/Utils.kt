package com.adriantache.poitracker.utils

import android.location.Location
import com.adriantache.poitracker.data.RegionList
import com.adriantache.poitracker.models.City
import com.adriantache.poitracker.models.POI
import kotlin.math.sqrt


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

    fun getDistance(location1: Location, lat: Double, long: Double): Float {
        val location2 = Location("")
        location2.latitude = lat
        location2.longitude = long

        return location1.distanceTo(location2)
    }

    //check if user is inside a city
    //assumption: city is a circle
    fun isInsideCity(location: Location): City? {
        RegionList.cities.forEach { city ->
            val cityRadius = getRadius(city.areaKm2)
            //add 10% to city radius as a safety margin
            if (getDistance(location, city.lat, city.long) <= cityRadius * 1.1) {
                return city
            }
        }

        return null
    }

    //get radius of circle in metres given the area in km2
    fun getRadius(area: Int): Double {
        return sqrt(area / Math.PI) * 1000
    }

    //order the list of locations by distance to current user location
    fun getListByDistance(location: Location, list: List<City>): List<City> {
        val orderedList = list.sortedBy {
            getDistance(location, it.lat, it.long)
        }

        //only return at most 100 result, which is the maximum number of geofences
        return if (list.size <= 100) {
            orderedList
        } else {
            orderedList.subList(0, 99)
        }
    }
}