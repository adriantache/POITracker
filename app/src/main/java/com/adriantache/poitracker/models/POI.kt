package com.adriantache.poitracker.models

data class POI(
    val name: String,
    override val lat: Double,
    override val long: Double,
    val category: String
) : Coordinates