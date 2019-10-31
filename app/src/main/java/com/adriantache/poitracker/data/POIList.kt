package com.adriantache.poitracker.data

import com.adriantache.poitracker.models.POI

/**
 * Holds all POIs.
 */
object POIList {
    /**
     * [Assumptions:]
     * city list is not empty
     * all POIs are inside a city
     * there are no duplicate POIs or names
     * there are no POIs confusingly close to the city edge
     * POI names don't contain underscores
     * there aren't more than 989 cities (due to how notification ID is set)
     */
    val values = listOf(
        POI("Epicerie Delices", 46.206169, 6.132623, "Grocery"),
        POI("Planete Charmilles", 46.208333, 6.124292, "Mall"),
        POI("Coop",46.207447, 6.1301, "Supermarket"),
        POI("Geosatis", 46.517494, 6.562019, "Business")
    )
}