package com.adriantache.poitracker

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.adriantache.poitracker.broadcastReceivers.GeofenceBroadcastReceiver
import com.adriantache.poitracker.data.POIList
import com.adriantache.poitracker.data.RegionList
import com.adriantache.poitracker.models.City
import com.adriantache.poitracker.models.POIExpanded
import com.adriantache.poitracker.utils.Utils
import com.adriantache.poitracker.utils.Utils.getCity
import com.adriantache.poitracker.utils.Utils.getListByDistance
import com.adriantache.poitracker.utils.Utils.getRadius
import com.adriantache.poitracker.utils.Utils.isInsideCity
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.Geofence.NEVER_EXPIRE
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers


private const val FIRST_LAUNCH = "first_launch"
private const val POI_LIST = "poi_list"
private const val POI_GEOFENCE_RADIUS = 200f

class MainActivity : AppCompatActivity() {
    private val disposables = CompositeDisposable()
    private lateinit var geofencingClient: GeofencingClient
    private lateinit var poiList: MutableList<POIExpanded>

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(this, GeofenceBroadcastReceiver::class.java)
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // addGeofences() and removeGeofences().
        PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        getPoiList()
    }

    private fun getPoiList() {
        //if this is the first launch, process the POI list
        //[assumption]: no new POIs are ever added since we are offline
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        val firstLaunch = sharedPref.getBoolean(FIRST_LAUNCH, true)
        if (firstLaunch) {
            processPOIs()
        } else {
            //otherwise, get already processed POI list from memory
            val poiString = sharedPref.getString(POI_LIST, null)
            if (poiString == null) {
                Toast.makeText(this, "Error getting POI list from storage!", Toast.LENGTH_SHORT)
                    .show()
            } else {
                val listType = object : TypeToken<MutableList<POIExpanded>>() {}.type
                poiList = Gson().fromJson(poiString, listType)

                if (poiList.isEmpty()) {
                    Toast.makeText(this, "Error getting POI list from storage!", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    //reset geofencing
                    //ensure we have necessary permissions before requesting location
                    if (!checkLocationPermissions()) {
                        requestLocationPermissions()
                    } else {
                        getUserLocation()
                    }
                }
            }
        }
    }

    //transform initial POI list to a smarter list to set up fewer geofences with less frequent polling
    //[IDEA:] generate custom areas depending on distance instead of relying on predefined regions
    private fun processPOIs() {
        //todo bin each location to a larger location (city) on first launch
        val observable = Single.just(POIList.values)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())

        disposables += observable.subscribe({
            poiList = mutableListOf()

            for (poi in it) {
                val (city, distance) = getCity(poi)

                val generatedPoi = POIExpanded(
                    poi.name,
                    poi.lat,
                    poi.long,
                    poi.category,
                    city,
                    distance
                )

                poiList.add(generatedPoi)
            }

            if (poiList.isNotEmpty()) {
                //set first launch flag to prevent reprocessing the list
                val sharedPref = getPreferences(Context.MODE_PRIVATE).edit()
                sharedPref.putBoolean(FIRST_LAUNCH, false)

                //save POI list to storage
                //todo use Room for this instead
                sharedPref.putString(POI_LIST, Gson().toJson(poiList))

                sharedPref.apply()

                //ensure we have necessary permissions before requesting location
                if (!checkLocationPermissions()) {
                    requestLocationPermissions()
                } else getUserLocation()
            }
        }, { error ->
            Toast.makeText(this, "Error creating expanded POI list!", Toast.LENGTH_SHORT).show()
            error.printStackTrace()
        })
    }

    //get the current location of the user to determine geofencing strategy (city or POI level)
    private fun getUserLocation() {
        //get user location
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val observable = Single.create<Location> {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                it.onSuccess(location)
            }

            fusedLocationClient.lastLocation.addOnFailureListener { error ->
                it.onError(error)
            }
        }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())

        disposables += observable.subscribe({
            setUpGeofencing(it)
        }, { error ->
            Toast.makeText(this, "Error getting user location!", Toast.LENGTH_SHORT).show()
            error.printStackTrace()

            //todo set up city-level geofencing in this situation?
//            setUpCityGeofences(null)
        })
    }

    //set up geofencing to notify the user based on the defined POIs
    private fun setUpGeofencing(userLocation: Location) {
        val city = isInsideCity(userLocation)

        //todo remove this
        setUpCityGeofences(userLocation)

        if (city == null) {
            //if user is outside city, setup city geofences based on flight time to nearest city
            //use rx
            setUpCityGeofences(userLocation)
        } else {
            //if user is within city, setup location geofences based on distance to nearest POI
            setUpPoiGeofences(userLocation)
        }
    }

    private fun setUpCityGeofences(userLocation: Location) {
        geofencingClient = LocationServices.getGeofencingClient(this)

        val observable = Single.create<List<City>> {
            //get the list as ordered by distance
            val orderedList = getListByDistance(userLocation, RegionList.cities)

            if (orderedList.isNotEmpty()) {
                it.onSuccess(orderedList)
            } else {
                throw IllegalArgumentException("Error sorting list by distance.")
            }
        }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())

        disposables += observable.subscribe({
            val geofenceList = mutableListOf<Geofence>()

            it.forEach { city ->
                //calculate max speed as 100kph in m/ms
                val maxSpeed = (100 * 1000) / (60 * 60 * 1000)
                var notificationResponsiveness =
                    (Utils.getDistance(userLocation, city.lat, city.long) / maxSpeed).toInt()
                //prevent setting an unreasonably low (<2 min) or high (>1 hour) value
                if (notificationResponsiveness < 1000 * 60 * 2) {
                    notificationResponsiveness = 12_000
                } else if (notificationResponsiveness > 1000 * 60 * 60) {
                    notificationResponsiveness = 3_600_000
                }

                geofenceList.add(
                    Geofence.Builder()
                        .setRequestId("CITY_${city.name}")
                        //loitering delay to prevent triggering geofence enter/exit events prematurely
                        //todo tweak this value
//                        .setLoiteringDelay(1000 * 30)
                        //make geofence less responsive the farther away we are from a city
                        //todo tweak this value
                        .setNotificationResponsiveness(notificationResponsiveness)
                        .setExpirationDuration(NEVER_EXPIRE)
                        .setCircularRegion(
                            city.lat,
                            city.long,
                            getRadius(city.areaKm2).toFloat()
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

                geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)?.run {
                    addOnSuccessListener {
                        Toast.makeText(
                            this@MainActivity,
                            "City geofences successfully added!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    addOnFailureListener {
                        Toast.makeText(
                            this@MainActivity,
                            "City geofences could not be added!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }, { error ->
            Toast.makeText(this, "Error setting up city geofences!", Toast.LENGTH_SHORT).show()
            error.printStackTrace()
        })
    }

    private fun setUpPoiGeofences(userLocation: Location) {
        geofencingClient = LocationServices.getGeofencingClient(this)

        val observable = Single.create<List<POIExpanded>> {
            //get the list as ordered by distance
            val orderedList = getListByDistance(userLocation, poiList)

            if (orderedList.isNotEmpty()) {
                it.onSuccess(orderedList)
            } else {
                throw IllegalArgumentException("Error sorting list by distance.")
            }
        }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())

        disposables += observable.subscribe({
            val geofenceList = mutableListOf<Geofence>()

            it.forEach { poi ->
                geofenceList.add(
                    Geofence.Builder()
                        .setRequestId("POI_${poi.name}") //again assuming names are unique
                        //loitering delay to prevent triggering geofence enter/exit events prematurely
                        //todo tweak this value
//                        .setLoiteringDelay(1000 * 30)
                        //todo tweak or automate this value
                        .setNotificationResponsiveness(5 * 60 * 1000)
                        .setExpirationDuration(NEVER_EXPIRE)
                        .setCircularRegion(
                            poi.lat,
                            poi.long,
                            POI_GEOFENCE_RADIUS
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

                geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)?.run {
                    addOnSuccessListener {
                        Toast.makeText(
                            this@MainActivity,
                            "POI geofences successfully added!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    addOnFailureListener {
                        Toast.makeText(
                            this@MainActivity,
                            "POI geofences could not be added!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }, { error ->
            Toast.makeText(this, "Error setting up POI geofences!", Toast.LENGTH_SHORT).show()
            error.printStackTrace()
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.clear()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        getUserLocation()
    }

    private fun checkLocationPermissions(): Boolean {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED ||
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return false

        return true
    }

    private fun requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ),
                    1
                )
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    1
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                2
            )
        }
    }
}

operator fun CompositeDisposable.plusAssign(disposable: Disposable) {
    add(disposable)
}