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
import com.adriantache.poitracker.utils.Constants.CITY_LABEL
import com.adriantache.poitracker.utils.Constants.NOTIFICATION_CHANNEL_CITY
import com.adriantache.poitracker.utils.Constants.NOTIFICATION_CHANNEL_POI
import com.adriantache.poitracker.utils.Constants.POI_LABEL
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
    private val disposables = CompositeDisposable() //todo figure out how to use this or remove it
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
            if (context != null) {
                consumeEvents(geofenceTransition, triggeringGeofences, context)
            } else Log.e(TAG, "Error getting context!")

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

                if (labels.size < 2) Log.e(TAG, "Geofence tag error! ${it.requestId} => $labels")

                val type = labels[0]
                val name = labels[1]

                //initialize notification manager
                notificationManager =
                    context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

                if (type == CITY_LABEL) {
                    if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
                        //todo add logic for entering city
                        //trigger city notification
                        triggerCityNotification(name, context)
                        //remove all city geofences
                        //add POI geofences
                        //add current city exit geofence

                    } else if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
                        //todo add logic for exiting city
                        //dismiss city notification
                        dismissCityNotification()
                        //dismiss POI notification
                        //remove all POI geofences
                        //add city geofences

                    }
                } else if (type == POI_LABEL) {
                    if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
                        //todo add logic for entering POI
                        //trigger POI notification
                        triggerPOINotification(name, context)

                    } else if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
                        //todo add logic for exiting POI
                        //dismiss POI notification
                        dismissPOINotification()
                    }
                }
            }, { error -> error.printStackTrace() })
        )
    }

    private fun triggerCityNotification(name: String, context: Context) {
        val notificationTitle = "POITracker: Entered city."
        val notificationText = "Welcome to $name! There are POI in this city."

        buildCityNotificationChannel()

        //create an intent to open the main activity
        val intent = Intent(context, MainActivity::class.java)
        //put together the PendingIntent
        val pendingIntent = PendingIntent.getActivity(
            context,
            10, //todo add city ID here
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
        notificationManager.notify(10, notificationBuilder.build()) //todo add city ID here
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

    private fun dismissCityNotification(id: Int = 0) {
        notificationManager.cancel(10 + id)
    }

    private fun triggerPOINotification(name: String, context: Context) {
        val notificationTitle = "POITracker: Near POI."
        val notificationText = "Welcome to $name!"

        buildPOINotificationChannel()

        //create an intent to open the main activity
        val intent = Intent(context, MainActivity::class.java)
        //put together the PendingIntent
        val pendingIntent = PendingIntent.getActivity(
            context,
            1000, //todo add POI ID here
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
        notificationManager.notify(1000, notificationBuilder.build()) //todo add POI ID here
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

    private fun dismissPOINotification(id: Int = 0) {
        notificationManager.cancel(1000 + id)
    }

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
