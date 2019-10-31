package com.adriantache.poitracker.data

import com.adriantache.poitracker.models.City

/**
 * Holds all known larger regions. Currently that means cities
 * but it can be expanded to neighbourhoods of course or auto-generated.
 */

object RegionList {
    val cities = listOf(
        City(1, "Geneva", 46.2, 6.15, 16),
        City(2, "Lausanne", 46.519833, 6.6335, 42)
    )
}