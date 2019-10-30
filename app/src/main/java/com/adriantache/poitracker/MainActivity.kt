package com.adriantache.poitracker

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.adriantache.poitracker.data.POIList
import com.adriantache.poitracker.models.POIExpanded
import com.adriantache.poitracker.utils.Utils.getCity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import androidx.core.app.ComponentActivity
import androidx.core.app.ComponentActivity.ExtraData
import androidx.core.content.ContextCompat.getSystemService
import android.icu.lang.UCharacter.GraphemeClusterBreak.T



private const val FIRST_LAUNCH = "first_launch"
private const val POI_LIST = "poi_list"

class MainActivity : AppCompatActivity() {
    private val disposables = CompositeDisposable()
    private lateinit var poiList: MutableList<POIExpanded>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //if this is the first launch, process the POI list
        //[assumption]: no new POIs are ever added since we are offline
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        val firstLaunch = sharedPref.getBoolean(FIRST_LAUNCH, true)
        if (firstLaunch) {
            processPOIs()
        } else {
            //otherwise, get already processed POI list from memory
            val poiString = sharedPref.getString(POI_LIST, null)
            if(poiString == null) {
                Toast.makeText(this, "Error getting POI list from storage!", Toast.LENGTH_SHORT).show()
            } else {
                val listType = object : TypeToken<MutableList<POIExpanded>>() {}.type
                poiList = Gson().fromJson(poiString, listType)
            }

            if(poiList.isEmpty()) {
                Toast.makeText(this, "Error getting POI list from storage!", Toast.LENGTH_SHORT).show()
            } else {
                //todo reset geofencing
            }
        }
    }

    //transform initial POI list to a smarter list
    //[IDEA:] generate custom areas depending on distance instead of relying on predefined regions
    private fun processPOIs() {
        //todo bin each location to a larger location (city) on first launch
        poiList = mutableListOf()

        val observable = Observable.just(POIList.values)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())

        disposables += observable.subscribe({
            for (poi in it) {
                val (city, distance) = getCity(poi)

                val generatedPoi = POIExpanded(
                    poi.name,
                    poi.location,
                    poi.category,
                    city,
                    distance
                )

                poiList.add(generatedPoi)
            }

            if(poiList.isNotEmpty()){
                //set first launch flag to prevent reprocessing the list
                val sharedPref = getPreferences(Context.MODE_PRIVATE).edit()
                sharedPref.putBoolean(FIRST_LAUNCH, false)

                //save POI list to storage
                //todo use Room for this instead
                sharedPref.putString(POI_LIST, Gson().toJson(poiList))

                sharedPref.apply()

                //todo define geofencing
            }
        }, { error ->
            Toast.makeText(this, "Error creating expanded POI list!", Toast.LENGTH_SHORT).show()
            error.printStackTrace()
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.clear()
    }
}

operator fun CompositeDisposable.plusAssign(disposable: Disposable) {
    add(disposable)
}