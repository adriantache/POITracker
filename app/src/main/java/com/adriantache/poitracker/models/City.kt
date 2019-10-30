package com.adriantache.poitracker.models

data class City(
    val name: String,
    override val lat: Double,
    override val long: Double,
    val areaKm2: Int
) : Coordinates