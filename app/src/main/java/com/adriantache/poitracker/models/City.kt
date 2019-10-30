package com.adriantache.poitracker.models

import android.location.Location

data class City(
    val name: String,
    val location: Location,
    val areaKm2: Int
)