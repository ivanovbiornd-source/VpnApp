package com.vpnapp.service

import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.util.Log
import com.google.android.gms.location.*
import com.vpnapp.model.AppSettings
import com.vpnapp.model.VpnState
import com.vpnapp.utils.PreferencesManager
import com.vpnapp.utils.NotificationHelper

class LocationService : Service() {

    companion object {
        const val TAG = "LocationService"
        const val ACTION_START = "com.vpnapp.START_LOCATION"
        const val ACTION_STOP = "com.vpnapp.STOP_LOCATION"
        const val NOTIF_ID = 1003
    }

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var prefs: PreferencesManager
    private var wasInHomeZone = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { handleLocation(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        prefs = PreferencesManager(this)
        NotificationHelper.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        val notification = NotificationHelper.buildVpnNotification(
            this, "GPS мониторинг активен", false
        )
        startForeground(NOTIF_ID, notification)

        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30_000L)
            .setMinUpdateDistanceMeters(20f)
            .build()

        try {
            fusedClient.requestLocationUpdates(request, locationCallback, mainLooper)
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted: ${e.message}")
            stopSelf()
        }
    }

    private fun stopTracking() {
        fusedClient.removeLocationUpdates(locationCallback)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleLocation(location: Location) {
        val settings = prefs.loadSettings()
        if (!settings.gpsAutoEnabled || settings.homeLatitude == 0.0) return

        val homeLoc = Location("home").apply {
            latitude = settings.homeLatitude
            longitude = settings.homeLongitude
        }

        val distance = location.distanceTo(homeLoc)
        val inHomeZone = distance <= settings.homeRadius

        Log.d(TAG, "Distance to home: ${distance.toInt()}m, inZone=$inHomeZone")

        if (inHomeZone && !wasInHomeZone) {
            // Entered home zone → disconnect VPN
            Log.d(TAG, "Entered home zone — disconnecting VPN")
            if (VpnService.currentState == VpnState.CONNECTED) {
                sendBroadcast(Intent(VpnService.ACTION_STOP).apply {
                    setClass(this@LocationService, VpnService::class.java)
                })
                NotificationHelper.showNotification(
                    this, "VPN отключён",
                    "Вы дома — VPN автоматически отключён"
                )
            }
        } else if (!inHomeZone && wasInHomeZone) {
            // Left home zone → connect VPN
            Log.d(TAG, "Left home zone — connecting VPN")
            if (VpnService.currentState == VpnState.DISCONNECTED) {
                val servers = PreferencesManager(this).loadServers()
                val selectedId = settings.selectedServerId
                val server = servers.firstOrNull { it.id == selectedId } ?: servers.firstOrNull()
                server?.let {
                    val intent = Intent(VpnService.ACTION_START).apply {
                        setClass(this@LocationService, VpnService::class.java)
                        putExtra(VpnService.EXTRA_SERVER, it)
                    }
                    startService(intent)
                    NotificationHelper.showNotification(
                        this, "VPN включён",
                        "Вы покинули домашнюю зону — VPN автоматически подключён"
                    )
                }
            }
        }

        wasInHomeZone = inHomeZone
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
