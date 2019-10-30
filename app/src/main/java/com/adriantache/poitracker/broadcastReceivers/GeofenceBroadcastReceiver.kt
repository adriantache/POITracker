package com.adriantache.poitracker.broadcastReceivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.adriantache.poitracker.utils.Utils.getGeofenceErrorString
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.LocationServices
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers


private const val TAG = "GeofenceBroadcastReceiver"

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    private val disposables = CompositeDisposable()

    override fun onReceive(context: Context?, intent: Intent?) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        if (geofencingEvent.hasError()) {
            val errorMessage = getGeofenceErrorString(
                geofencingEvent.errorCode
            )
            Log.e(TAG, errorMessage)
            return
        }

        // Get the transition type.
        val geofenceTransition = geofencingEvent.geofenceTransition

        // Test that the reported transition was of interest.
        if (geofenceTransition in setOf(
                Geofence.GEOFENCE_TRANSITION_ENTER,
                Geofence.GEOFENCE_TRANSITION_EXIT
            )
        ) {
            // Get the geofences that were triggered. A single event can trigger
            // multiple geofences.
            val triggeringGeofences = geofencingEvent.triggeringGeofences

            //process each event individually
            consumeEvents(geofenceTransition, triggeringGeofences)

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

    //todo [IMPORTANT] dispose of observer
    private fun consumeEvents(geofenceTransition: Int, triggeringGeofences: List<Geofence>) {
        val observable = Observable.just(triggeringGeofences)
            .subscribeOn(Schedulers.io())
            .flatMap {
                Observable.fromIterable(it)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
            }
            .observeOn(AndroidSchedulers.mainThread())

        disposables.add(
            observable.subscribe({
                val labels = it.requestId.split('_')

                if (labels.size < 2) Log.e(TAG, "Geofence tag error! ${it.requestId} => $labels")

                if (labels[0] == "CITY") {
                    if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
                        //todo add logic for entering city
                        //trigger city notification
                        //remove all city geofences
                        //add POI geofences
                        //add current city exit geofence

                    } else if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
                        //todo add logic for exiting city
                        //dismiss city notification
                        //dismiss POI notification
                        //remove all POI geofences
                        //add city geofences

                    }
                } else if (labels[0] == "POI") {
                    if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
                        //todo add logic for entering POI
                        //trigger POI notification


                    } else if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
                        //todo add logic for exiting POI
                        //dismiss POI notification

                    }
                }
            }, { error -> error.printStackTrace() })
        )
    }

    private fun getGeofenceTransitionDetails(
        geofenceTransition: Int,
        triggeringGeofences: List<Geofence>
    ): String {
        var resultString = ""

        resultString += when (geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "Entered geofence area."
            Geofence.GEOFENCE_TRANSITION_EXIT -> "Exited geofence area."
            Geofence.GEOFENCE_TRANSITION_DWELL -> "Dwelling in geofence area."
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
