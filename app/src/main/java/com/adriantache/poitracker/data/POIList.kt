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
     */
    val values = listOf(
        POI("Epicerie Delices", 46.206169, 6.132623, "Grocery"),
        POI("Planete Charmilles", 46.208333, 6.124292, "Mall"),
        POI("Geosatis", 46.517494, 6.562019, "Business")
    )
}