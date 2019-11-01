package com.adriantache.poitracker.models

data class POIExpanded(
    val id: Int,
    val name: String,
    override val lat: Double,
    override val long: Double,
    val category: String,
    val city: City
) : Coordinates