package com.adriantache.poitracker.models

data class City(
    val id: Int,
    val name: String,
    override val lat: Double,
    override val long: Double,
    val areaKm2: Int
) : Coordinates