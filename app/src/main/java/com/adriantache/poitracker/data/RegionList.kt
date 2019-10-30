package com.adriantache.poitracker.data

import android.location.Location
import com.adriantache.poitracker.models.City

/**
 * Holds all known larger regions. Currently that means cities
 * but it can be expanded to neighbourhoods of course.
 */

object RegionList {
    val cities: List<City>

    init {
        val genevaLocation = Location("gps")
        genevaLocation.latitude = 46.2
        genevaLocation.longitude = 6.15

        val lausanneLocation = Location("gps")
        lausanneLocation.latitude = 46.519833
            lausanneLocation.longitude = 6.6335

        cities = listOf(
            City("Geneva", genevaLocation, 16),
            City("Lausanne", lausanneLocation, 42)
        )

    }
}