package com.adriantache.poitracker.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.util.Log
import com.adriantache.poitracker.broadcastReceivers.GeofenceBroadcastReceiver
import com.adriantache.poitracker.data.RegionList
import com.adriantache.poitracker.models.City
import com.adriantache.poitracker.models.POIExpanded
import com.adriantache.poitracker.plusAssign
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

private const val TAG = "Geofencing_utils"

object GeofencingUtils {
    val disposables = CompositeDisposable()

    //get the general pending intent that registers the BroadcastReceiver to listen to Geofence events
    fun getPendingIntent(context: Context): PendingIntent? {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // addGeofences() and removeGeofences().
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    //add geofences for POIs
    fun addPOIGeofences(
        cityName: String,
        poiList: List<POIExpanded>,
        context: Context,
        userLocation: Location? = null
    ) {
        val geofencingClient = LocationServices.getGeofencingClient(context)

        val observable = Single.create<List<POIExpanded>> {
            //only accept POIs from that city
            val filteredList = poiList.filter { poi ->
                poi.city.name == cityName
            }

            if (userLocation == null) it.onSuccess(filteredList)
            else {
                //get the list as ordered by distance
                val orderedList = Utils.getListByDistance(userLocation, filteredList)

                if (orderedList.isNotEmpty()) {
                    it.onSuccess(orderedList)
                } else {
                    throw IllegalArgumentException("Error sorting list by distance.")
                }
            }
        }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())

        disposables += observable.subscribe({
            val geofenceList = mutableListOf<Geofence>()

            it.forEach { poi ->
                geofenceList.add(
                    Geofence.Builder()
                        .setRequestId("${Constants.POI_LABEL}_${poi.id}") //again assuming names are unique
                        //loitering delay to prevent triggering geofence enter/exit events prematurely
                        //todo tweak this value
//                        .setLoiteringDelay(1000 * 30)
                        //todo tweak or automate this value
                        .setNotificationResponsiveness(5 * 60 * 1000)
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .setCircularRegion(
                            poi.lat,
                            poi.long,
                            Constants.POI_GEOFENCE_RADIUS
                        )
                        .setTransitionTypes(
                            Geofence.GEOFENCE_TRANSITION_ENTER or
                                    Geofence.GEOFENCE_TRANSITION_EXIT
                        )
                        .build()
                )

                val geofencingRequest = GeofencingRequest.Builder().apply {
                    setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER) //todo change this to dwell eventually
                        .addGeofences(geofenceList)
                }.build()

                geofencingClient.addGeofences(geofencingRequest, getPendingIntent(context))?.run {
                    addOnSuccessListener {
                        Log.i(TAG, "POI geofences successfully added!")
                    }
                    addOnFailureListener {
                        Log.e(TAG, "POI geofences could not be added!")
                    }
                }
            }
        }, { error ->
            Log.e(TAG, "Current city geofence could not be added!")
            error.printStackTrace()
        })
    }

    //add geofences for cities
    fun addCityGeofences(
        context: Context,
        userLocation: Location? = null
    ) {
        val geofencingClient = LocationServices.getGeofencingClient(context)

        val observable = Single.create<List<City>> {
            if (userLocation == null) it.onSuccess(RegionList.cities)
            else {
                //get the list as ordered by distance
                val orderedList = Utils.getListByDistance(userLocation, RegionList.cities)

                if (orderedList.isNotEmpty()) {
                    it.onSuccess(orderedList)
                } else {
                    throw IllegalArgumentException("Error sorting list by distance.")
                }
            }
        }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())

        disposables += observable.subscribe({
            val geofenceList = mutableListOf<Geofence>()

            it.forEach { city ->
                //calculate max speed as 100kph in m/ms
                val maxSpeed = (100 * 1000) / (60 * 60 * 1000)
                var notificationResponsiveness =
                    if (userLocation == null) 5 * 60 * 1000
                    else (Utils.getDistance(userLocation, city.lat, city.long) / maxSpeed).toInt()
                //prevent setting an unreasonably low (<2 min) or high (>1 hour) value
                if (notificationResponsiveness < 1000 * 60 * 2) {
                    notificationResponsiveness = 12_000
                } else if (notificationResponsiveness > 1000 * 60 * 60) {
                    notificationResponsiveness = 3_600_000
                }

                geofenceList.add(
                    Geofence.Builder()
                        .setRequestId("${Constants.CITY_LABEL}_${city.id}")
                        //loitering delay to prevent triggering geofence enter/exit events prematurely
                        //todo tweak this value
//                        .setLoiteringDelay(1000 * 30)
                        //make geofence less responsive the farther away we are from a city
                        //todo tweak this value
                        .setNotificationResponsiveness(notificationResponsiveness)
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .setCircularRegion(
                            city.lat,
                            city.long,
                            Utils.getRadius(city.areaKm2).toFloat()
                        )
                        .setTransitionTypes(
                            Geofence.GEOFENCE_TRANSITION_ENTER or
                                    Geofence.GEOFENCE_TRANSITION_EXIT
                        )
                        .build()
                )

                val geofencingRequest = GeofencingRequest.Builder().apply {
                    setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER) //todo change this to dwell eventually
                        .addGeofences(geofenceList)
                }.build()

                geofencingClient.addGeofences(geofencingRequest, getPendingIntent(context))?.run {
                    addOnSuccessListener {
                        Log.i(TAG, "Cities geofences successfully added!")
                    }
                    addOnFailureListener {
                        Log.e(TAG, "Cities geofences could not be added!")
                    }
                }
            }
        }, { error ->
            Log.e(TAG, "Cities geofences could not be added!")
            error.printStackTrace()
        })
    }
}

operator fun CompositeDisposable.plusAssign(disposable: Disposable) {
    add(disposable)
}