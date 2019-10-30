package com.adriantache.poitracker.data

import android.location.Location
import com.adriantache.poitracker.models.POI

object POIList {
    /**
     * making assumptions:
     * city list is not empty
     * all POIs are inside a city
     * there are no duplicate POIs or names
     */
    val values: List<POI>

    init {
        val epicerieLocation = Location("gps")
        epicerieLocation.latitude = 46.206169
        epicerieLocation.longitude = 6.132623

        val charmillesLocation = Location("gps")
        charmillesLocation.latitude = 46.208333
        charmillesLocation.longitude = 6.124292

        val geosatisLocation = Location("gps")
        geosatisLocation.latitude = 46.517494
        geosatisLocation.longitude = 6.562019

        values = listOf(
            POI("Epicerie Delices", epicerieLocation, "Grocery"),
            POI("Planete Charmilles", charmillesLocation, "Mall"),
            POI("Geosatis", geosatisLocation, "Business")
        )
    }
}