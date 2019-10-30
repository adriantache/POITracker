package com.adriantache.poitracker.models

import android.location.Location

data class POIExpanded(
    val name: String,
    val location: Location,
    val category: String,
    val city: City,
    val distanceFromCity: Float
)