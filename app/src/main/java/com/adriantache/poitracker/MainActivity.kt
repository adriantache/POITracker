package com.adriantache.poitracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.adriantache.poitracker.data.POIList
import com.adriantache.poitracker.models.POIExpanded
import com.adriantache.poitracker.utils.Utils.getCity
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers


private const val FIRST_LAUNCH = "first_launch"
private const val POI_LIST = "poi_list"

class MainActivity : AppCompatActivity() {
    private val disposables = CompositeDisposable()
    private lateinit var poiList: MutableList<POIExpanded>

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
        val observable = Observable.just(POIList.values)
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
        })
    }

    //set up geofencing to notify the user based on the defined POIs
    private fun setUpGeofencing(userLocation: Location) {
        Log.d("TAG", userLocation.toString() + poiList.toString())

        //if user is outside city, setup city geofences based on flight time to nearest city
        //use rx

        //if user is within city, setup location geofences based on distance to nearest POI
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