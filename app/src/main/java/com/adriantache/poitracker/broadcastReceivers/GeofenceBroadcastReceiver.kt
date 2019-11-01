package com.adriantache.poitracker.broadcastReceivers

import android.app.Notification
import android.app.Notification.CATEGORY_EVENT
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.util.Log
import com.adriantache.poitracker.MainActivity
import com.adriantache.poitracker.R
import com.adriantache.poitracker.data.RegionList
import com.adriantache.poitracker.models.City
import com.adriantache.poitracker.models.POIExpanded
import com.adriantache.poitracker.utils.Constants.CITY_LABEL
import com.adriantache.poitracker.utils.Constants.NOTIFICATION_CHANNEL_CITY
import com.adriantache.poitracker.utils.Constants.NOTIFICATION_CHANNEL_POI
import com.adriantache.poitracker.utils.Constants.POI_LABEL
import com.adriantache.poitracker.utils.GeofencingUtils.addCityGeofences
import com.adriantache.poitracker.utils.GeofencingUtils.addPOIGeofences
import com.adriantache.poitracker.utils.GeofencingUtils.getPendingIntent
import com.adriantache.poitracker.utils.Utils
import com.adriantache.poitracker.utils.Utils.getGeofenceErrorString
import com.adriantache.poitracker.utils.Utils.loadPOIList
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers


private const val TAG = "GeofenceBroadcastReceiver"

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    private val disposables =
        CompositeDisposable() //todo figure out how to use this in a BroadcastReceiver
    private lateinit var notificationManager: NotificationManager

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
            if (context != null && intent != null) {
                consumeEvents(geofenceTransition, triggeringGeofences, context)
            } else Log.e(TAG, "Error getting context or intent!")

            // Get the transition details as a String.
            val geofenceTransitionDetails = getGeofenceTransitionDetails(
                geofenceTransition,
                triggeringGeofences
            )
            Log.i(TAG, geofenceTransitionDetails)
        } else {
            // Log the error.
            Log.e(TAG, "Geofence transition invalid type! $geofenceTransition")
        }
    }

    //todo [IMPORTANT] dispose of observer
    private fun consumeEvents(
        geofenceTransition: Int,
        triggeringGeofences: List<Geofence>,
        context: Context
    ) {
        val observable = Observable.just(triggeringGeofences)
            .subscribeOn(Schedulers.io())
            //using a flatMap to send individual events because they need to be processed sequentially
            .flatMap {
                Observable.fromIterable(it)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
            }
            .observeOn(AndroidSchedulers.mainThread())

        disposables.add(
            observable.subscribe({
                val labels = it.requestId.split('_')

                if (labels.size < 2) {
                    Log.e(TAG, "Geofence tag parsing error! ${it.requestId} => $labels")
                }

                val type = labels[0]
                val id: Int = labels[1].toInt()

                //initialize notification manager
                notificationManager =
                    context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

                if (type == CITY_LABEL) {
                    if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
                        Log.i(TAG, "Entered city (id = $id)")

                        //get city from ID
                        val city = RegionList.cities.find { city -> city.id == id }

                        if (city == null) {
                            Log.e(TAG, "Cannot find City $id in ${RegionList.cities}!")
                            return@subscribe
                        }

                        //trigger city notification
                        triggerCityNotification(city, context)
                        //remove all city geofences
                        //add current city exit geofence
                        //add POI geofences
                        removeGeofences(context, city)

                    } else if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
                        Log.i(TAG, "Exited city (id = $id)")

                        //dismiss city notification
                        //dismiss POI notification
                        dismissAllNotifications()
                        //remove all POI geofences
                        //add city geofences
                        removeGeofences(context)
                    }
                } else if (type == POI_LABEL) {
                    if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
                        Log.i(TAG, "Entered POI (id = $id)")

                        val poiList = loadPOIList(context)
                        if (poiList.isEmpty()) {
                            Log.e(TAG, "Cannot fetch POI list!")
                            return@subscribe
                        }

                        val poi = poiList.find { poi -> poi.id == id }

                        //trigger POI notification
                        if (poi != null) triggerPOINotification(poi, context)
                        else Log.e(TAG, "Cannot find POI $id in $poiList!")
                    } else if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
                        Log.i(TAG, "Exited POI (id = $id)")

                        //dismiss POI notification
                        dismissPOINotification(id)
                    }
                }
            }, { error -> error.printStackTrace() })
        )
    }

    private fun triggerCityNotification(city: City, context: Context) {
        val notificationTitle = "POITracker: Entered city."
        val notificationText = "Welcome to ${city.name}! There are POI in this city."

        buildCityNotificationChannel()

        //create an intent to open the main activity
        val intent = Intent(context, MainActivity::class.java)
        //put together the PendingIntent
        val pendingIntent = PendingIntent.getActivity(
            context,
            10 + city.id,
            intent,
            //updating current notification if it exists in order to prevent showing the same notification twice
            FLAG_UPDATE_CURRENT
        )

        //build the notification
        val notificationBuilder = Notification.Builder(
            context,
            NOTIFICATION_CHANNEL_CITY
        )
            //not wasting time with the icon
            .setSmallIcon(R.drawable.ic_location_on)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setContentIntent(pendingIntent)
            .setCategory(CATEGORY_EVENT)
            .setAutoCancel(true)

        //trigger the notification
        //we give each notification the ID of the city,
        //to ensure they all show up and there are no duplicates
        notificationManager.notify(10 + city.id, notificationBuilder.build())
    }

    private fun buildCityNotificationChannel() {
        //verify if channel is already built before building it
        val channel = notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_CITY)
        if (channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE) return

        //define the importance level of the notification
        val importance = IMPORTANCE_HIGH
        val description = "Shows notifications about whether there are POI inside the current city."

        //build the actual notification channel, giving it a unique ID and name
        val newChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_CITY,
            NOTIFICATION_CHANNEL_CITY,
            importance
        ).apply {
            this.description = description
        }

        // Register the channel with the system
        notificationManager.createNotificationChannel(newChannel)
    }

    private fun dismissAllNotifications() {
        notificationManager.cancelAll()
    }

    private fun triggerPOINotification(poi: POIExpanded, context: Context) {
        val notificationTitle = "POITracker: Near POI."
        val notificationText = "Welcome to ${poi.name} (${poi.category})!"

        buildPOINotificationChannel()

        //create an intent to open the main activity
        val intent = Intent(context, MainActivity::class.java)
        //put together the PendingIntent
        val pendingIntent = PendingIntent.getActivity(
            context,
            1000 + poi.id,
            intent,
            //updating current notification if it exists in order to prevent showing the same notification twice
            FLAG_UPDATE_CURRENT
        )

        //build the notification
        val notificationBuilder = Notification.Builder(
            context,
            NOTIFICATION_CHANNEL_POI
        )
            //not wasting time with the icon
            .setSmallIcon(R.drawable.ic_location_on)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setContentIntent(pendingIntent)
            .setCategory(CATEGORY_EVENT)
            .setAutoCancel(true)

        //trigger the notification
        //we give each notification the ID of the city,
        //to ensure they all show up and there are no duplicates
        notificationManager.notify(1000 + poi.id, notificationBuilder.build())
    }

    private fun buildPOINotificationChannel() {
        //verify if channel is already built before building it
        val channel = notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_POI)
        if (channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE) return

        //define the importance level of the notification
        val importance = IMPORTANCE_HIGH
        val description = "Shows notifications about whether you are near a POI."

        //build the actual notification channel, giving it a unique ID and name
        val newChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_POI,
            NOTIFICATION_CHANNEL_POI,
            importance
        ).apply {
            this.description = description
        }

        // Register the channel with the system
        notificationManager.createNotificationChannel(newChannel)
    }

    private fun dismissPOINotification(id: Int) {
        notificationManager.cancel(1000 + id)
    }

    //remove all geofences, then add current city geofence and geofence for all POI in that city
    private fun removeGeofences(context: Context, city: City) {
        val completable = Completable.create { completable ->
            val geofencingClient = LocationServices.getGeofencingClient(context)

            geofencingClient.removeGeofences(getPendingIntent(context)).run {
                addOnSuccessListener {
                    // Geofences removed
                    completable.onComplete()
                    Log.d(TAG, "Successfully removed city geofences.")
                }
                addOnFailureListener { e ->
                    // Failed to remove geofences
                    completable.onError(e)
                }
            }
        }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())

        disposables.add(
            completable.subscribe({
                //add current city exit geofence
                addCityAndPOIGeofence(context, city)
            },
                { e ->
                    e.printStackTrace()
                })
        )
    }

    //remove all geofences, then add all cities geofences
    private fun removeGeofences(context: Context) {
        val completable = Completable.create { completable ->
            val geofencingClient = LocationServices.getGeofencingClient(context)

            geofencingClient.removeGeofences(getPendingIntent(context)).run {
                addOnSuccessListener {
                    // Geofences removed
                    completable.onComplete()
                    Log.d(TAG, "Successfully removed city geofences.")
                }
                addOnFailureListener { e ->
                    // Failed to remove geofences
                    completable.onError(e)
                }
            }
        }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())

        disposables.add(
            completable.subscribe({
                //add current city exit geofence
                addCityGeofences(context)
            },
                { e ->
                    e.printStackTrace()
                })
        )
    }

    private fun addCityAndPOIGeofence(context: Context, city: City) {
        val observable = Single.just(city)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())

        disposables.add(
            observable.subscribe({ c ->
                val geofence = Geofence.Builder()
                    .setRequestId("${CITY_LABEL}_${c.id}")
                    .setNotificationResponsiveness(5 * 60 * 1000)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setCircularRegion(
                        city.lat,
                        city.long,
                        Utils.getRadius(city.areaKm2).toFloat()
                    )
                    .setTransitionTypes(
                        Geofence.GEOFENCE_TRANSITION_EXIT
                    )
                    .build()

                val geofencingRequest = GeofencingRequest.Builder().apply {
                    setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                        .addGeofence(geofence)
                }.build()

                val geofencingClient = LocationServices.getGeofencingClient(context)
                geofencingClient.addGeofences(geofencingRequest, getPendingIntent(context))
                    ?.run {
                        addOnSuccessListener {
                            Log.i(TAG, "Current city geofence successfully added!")

                            val poiList = loadPOIList(context)
                            if (poiList.isEmpty()) {
                                Log.e(TAG, "Current city geofence could not be added!")
                            } else {
                                //add city level geofences
                                addPOIGeofences(city.name, poiList, context)
                            }
                        }
                        addOnFailureListener {
                            Log.e(TAG, "Current city geofence could not be added!")
                            it.printStackTrace()
                        }
                    }
            },
                { error ->
                    Log.e(TAG, "Cannot fetch POI list!")
                    error.printStackTrace()
                })
        )
    }

    //todo using this for debugging, should be removed eventually
    private fun getGeofenceTransitionDetails(
        geofenceTransition: Int,
        triggeringGeofences: List<Geofence>
    ): String {
        var resultString = ""

        resultString += when (geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "Entered geofence area(s):"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "Exited geofence area(s):"
            Geofence.GEOFENCE_TRANSITION_DWELL -> "Dwelling in geofence area(s):"
            else -> "Unknown transition $geofenceTransition!"
        }

        resultString += "\n"

        triggeringGeofences.forEach {
            resultString += it.requestId
            resultString += "\n"
        }

        return resultString
    }
}
