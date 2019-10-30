package com.adriantache.poitracker.broadcastReceivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.adriantache.poitracker.broadcastReceivers.GeofenceErrorMessages.GeofenceErrorMessages.getErrorString
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.LocationServices


//todo remove this and the logs
private const val TAG = "TAG-Geofence_receiver"

class CityGeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(TAG, intent?.toString() ?: "No intent received!")

        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent.hasError()) {
            val errorMessage = getErrorString(
                geofencingEvent.errorCode
            )
            Log.e(TAG, errorMessage)
            return
        }

        // Get the transition type.
        val geofenceTransition = geofencingEvent.geofenceTransition

        // Test that the reported transition was of interest.
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
            geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT
        ) {

            // Get the geofences that were triggered. A single event can trigger
            // multiple geofences.
            val triggeringGeofences = geofencingEvent.triggeringGeofences

            // Get the transition details as a String.
            val geofenceTransitionDetails = getGeofenceTransitionDetails(
                geofenceTransition,
                triggeringGeofences
            )

            // Send notification and log the transition details.
//            sendNotification(geofenceTransitionDetails)
            Log.i(TAG, geofenceTransitionDetails)
        } else {
            // Log the error.
            Log.e(TAG, "Geofence transition invalid type! $geofenceTransition")
        }
    }

    private fun getGeofenceTransitionDetails(
        geofenceTransition: Int,
        triggeringGeofences: List<Geofence>
    ): String {
        var resultString = ""

        resultString += when (geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "Entered geofence area."
            Geofence.GEOFENCE_TRANSITION_EXIT -> "Exited geofence area."
            else -> "Unknown transition $geofenceTransition!"
        }

        resultString += "\n"

        triggeringGeofences.forEach {
            resultString += it.requestId
            resultString += "\n"
        }

        return resultString
    }

    private fun removeGeofences(context: Context, intent: Intent) {
        val geofencingClient = LocationServices.getGeofencingClient(context)

        //todo move to rx
//        geofencingClient?.removeGeofences(intent)?.run {
//            addOnSuccessListener {
//                // Geofences removed
//                // ...
//            }
//            addOnFailureListener {
//                // Failed to remove geofences
//                // ...
//            }
    }
}


class GeofenceErrorMessages {
    /**
     * Geofence error codes mapped to error messages.
     */
    internal object GeofenceErrorMessages {

        /**
         * Returns the error string for a geofencing exception.
         */
        fun getErrorString(e: Exception): String {
            return if (e is ApiException) {
                getErrorString(e.statusCode)
            } else {
                "Unknown geofence error"
            }
        }

        /**
         * Returns the error string for a geofencing error code.
         */
        fun getErrorString(errorCode: Int): String {
            return when (errorCode) {
                GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE -> return "Geofence not available!"
                GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES -> return "Too many geofences!"
                GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS -> return "Too many pending intents!"
                else -> "Unknown geofence error"
            }
        }
    }
}