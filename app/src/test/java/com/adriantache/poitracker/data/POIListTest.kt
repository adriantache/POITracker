package com.adriantache.poitracker.data

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.*

internal class POIListTest {

    @Test
    fun getValues() {
        //GIVEN
        val poiList = POIList.values

        //WHEN
        val random = Random().nextInt(poiList.size)
        val poi = poiList[random]

        //THEN
        assertFalse(poi.name.isEmpty())
        assertFalse(poi.category.isEmpty())
        assertTrue(poi.lat >= -90 && poi.lat <= 90)
        assertTrue(poi.long >= -180 && poi.long <= 180)
    }
}