package com.adriantache.poitracker.utils

import com.google.android.gms.location.GeofenceStatusCodes
import org.junit.Assert.assertTrue
import org.junit.jupiter.api.Test

internal class UtilsTest {

    @Test
    fun getGeofenceErrorString() {
        //GIVEN
        val errorValue = GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE

        //WHEN
        val errorString = Utils.getGeofenceErrorString(errorValue)

        //THEN
        assertTrue(errorString == "Geofence not available!")
    }
}