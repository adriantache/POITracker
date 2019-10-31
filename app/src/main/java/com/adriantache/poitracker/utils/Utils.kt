package com.adriantache.poitracker.utils

import android.content.Context
import android.location.Location
import android.widget.Toast
import com.adriantache.poitracker.data.RegionList
import com.adriantache.poitracker.models.*
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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

    //get distance between to places in metres
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
    fun <T> getListByDistance(
        location: Location,
        list: List<T>
    ): List<T> where T : Coordinates, T : Distinct {
        val orderedList = list.sortedBy {
            getDistance(location, it.lat, it.long)
        }

        //only return at most 100 result, which is the maximum number of geofences
        return if (list.size <= 100) {
            regenerateIDs(orderedList)
        } else {
            orderedList.subList(0, 99)
        }
    }

    private fun <T> regenerateIDs(list: List<T>): List<T> where T : Distinct {
        list.forEachIndexed { index, element ->
            element.id = index + 1
        }

        return list
    }

    //translate geofence errors into Strings
    fun getGeofenceErrorString(errorCode: Int): String {
        return when (errorCode) {
            GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE -> return "Geofence not available!"
            GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES -> return "Too many geofences!"
            GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS -> return "Too many pending intents!"
            else -> "Unknown geofence error"
        }
    }

    //get list of processed POI from SharedPrefs, or an empty list
    fun loadPOIList(context: Context): List<POIExpanded> {
        var poiList = mutableListOf<POIExpanded>()

        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREFS, Context.MODE_PRIVATE)
        val poiString = sharedPref.getString(Constants.POI_LIST, null)
        if (poiString == null) {
            Toast.makeText(context, "Error getting POI list from storage!", Toast.LENGTH_SHORT)
                .show()
        } else {
            val listType = object : TypeToken<MutableList<POIExpanded>>() {}.type
            poiList = Gson().fromJson(poiString, listType)
        }

        return poiList
    }
}