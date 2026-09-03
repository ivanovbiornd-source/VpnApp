package com.vpnapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vpnapp.utils.PreferencesManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val settings = PreferencesManager(context).loadSettings()

        // Re-schedule timer if enabled
        if (settings.timerEnabled) {
            AlarmReceiver.scheduleDaily(context, settings)
        }

        // Re-start GPS tracking if enabled
        if (settings.gpsAutoEnabled) {
            val locIntent = Intent(LocationService.ACTION_START).apply {
                setClass(context, LocationService::class.java)
            }
            context.startForegroundService(locIntent)
        }
    }
}
