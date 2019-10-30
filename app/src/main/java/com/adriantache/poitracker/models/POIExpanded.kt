package com.adriantache.poitracker.models

data class POIExpanded(
    val name: String,
    val lat: Double,
    val long: Double,
    val category: String,
    val city: City,
    val distanceFromCity: Float
)