package com.adriantache.poitracker.broadcastReceivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

private const val TAG = "BootReceiver"

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(p0: Context?, p1: Intent?) {
        //todo set geofences after system boot
        //idea: keep sharedpref to indicate current geofence level
        Log.i(TAG, "Detected system reboot.")
    }
}