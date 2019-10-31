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
import com.adriantache.poitracker.models.POIExpanded
import com.adriantache.poitracker.utils.Constants.FIRST_LAUNCH
import com.adriantache.poitracker.utils.Constants.POI_LIST
import com.adriantache.poitracker.utils.Constants.SHARED_PREFS
import com.adriantache.poitracker.utils.GeofencingUtils.addCityGeofences
import com.adriantache.poitracker.utils.GeofencingUtils.addPOIGeofences
import com.adriantache.poitracker.utils.Utils.getCity
import com.adriantache.poitracker.utils.Utils.isInsideCity
import com.adriantache.poitracker.utils.Utils.loadPOIList
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

//TODO create main UI
//TODO reset geofences on device restart (or maybe periodically with workmanager?)

class MainActivity : AppCompatActivity() {
    private val disposables = CompositeDisposable()
    private lateinit var poiList: List<POIExpanded>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        getPoiList()
    }

    private fun getPoiList() {
        //if this is the first launch, process the POI list
        //[assumption]: no new POIs are ever added since we are offline
        val sharedPref = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE)
        val firstLaunch = sharedPref.getBoolean(FIRST_LAUNCH, true)
        if (firstLaunch) {
            processPOIs()
        } else {
            poiList = loadPOIList(this)

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

    //transform initial POI list to a smarter list to set up fewer geofences with less frequent polling
    //[IDEA:] generate custom areas depending on distance instead of relying on predefined regions
    private fun processPOIs() {
        //todo bin each location to a larger location (city) on first launch
        val observable = Single.just(POIList.values)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())

        disposables += observable.subscribe({
            val tempList = mutableListOf<POIExpanded>()

            it.forEachIndexed { index, poi ->
                val (city, distance) = getCity(poi)

                val generatedPoi = POIExpanded(
                    index + 1,
                    poi.name,
                    poi.lat,
                    poi.long,
                    poi.category,
                    city,
                    distance
                )

                tempList.add(generatedPoi)
            }

            if (tempList.isNotEmpty()) {
                poiList = tempList.toList()

                //set first launch flag to prevent reprocessing the list
                val sharedPref = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE).edit()
                sharedPref.putBoolean(FIRST_LAUNCH, false)

                //save POI list to storage
                //todo use Room for this instead, eventually
                sharedPref.putString(POI_LIST, Gson().toJson(poiList))

                sharedPref.apply()

                //ensure we have necessary permissions before requesting location
                if (!checkLocationPermissions()) {
                    requestLocationPermissions()
                } else getUserLocation()
            }
        },
            { error ->
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
                if (location == null) it.onError(java.lang.IllegalArgumentException("Location is null even though it shouldn't be..."))
                else it.onSuccess(location)
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

        if (city == null) {
            //if user is outside city, setup city geofences based on flight time to nearest city
            //use rx
            setUpCityGeofences(userLocation)
        } else {
            //if user is within city, setup location geofences based on distance to nearest POI
            setUpPoiGeofences(userLocation, city.name)
        }
    }

    private fun setUpCityGeofences(userLocation: Location) {
        addCityGeofences(this, userLocation)
    }

    private fun setUpPoiGeofences(userLocation: Location, cityName: String) {
        addPOIGeofences(cityName, poiList, this, userLocation)
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