package com.vpnapp.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.vpnapp.model.AppSettings
import com.vpnapp.utils.PreferencesManager
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
        const val ACTION_START_VPN = "com.vpnapp.alarm.START_VPN"
        const val ACTION_STOP_VPN = "com.vpnapp.alarm.STOP_VPN"
        const val REQUEST_START = 2001
        const val REQUEST_STOP = 2002

        fun scheduleDaily(context: Context, settings: AppSettings) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // Schedule START
            val startCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, settings.timerStartHour)
                set(Calendar.MINUTE, settings.timerStartMinute)
                set(Calendar.SECOND, 0)
                if (timeInMillis < System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            val startIntent = PendingIntent.getBroadcast(
                context, REQUEST_START,
                Intent(context, AlarmReceiver::class.java).apply { action = ACTION_START_VPN },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setRepeating(AlarmManager.RTC_WAKEUP, startCal.timeInMillis, AlarmManager.INTERVAL_DAY, startIntent)

            // Schedule STOP
            val stopCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, settings.timerStopHour)
                set(Calendar.MINUTE, settings.timerStopMinute)
                set(Calendar.SECOND, 0)
                if (timeInMillis < System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            val stopIntent = PendingIntent.getBroadcast(
                context, REQUEST_STOP,
                Intent(context, AlarmReceiver::class.java).apply { action = ACTION_STOP_VPN },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setRepeating(AlarmManager.RTC_WAKEUP, stopCal.timeInMillis, AlarmManager.INTERVAL_DAY, stopIntent)

            Log.d(TAG, "Alarms scheduled: start ${settings.timerStartHour}:${settings.timerStartMinute}, " +
                    "stop ${settings.timerStopHour}:${settings.timerStopMinute}")
        }

        fun cancel(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            listOf(REQUEST_START, REQUEST_STOP).forEach { reqCode ->
                val pi = PendingIntent.getBroadcast(
                    context, reqCode,
                    Intent(context, AlarmReceiver::class.java),
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                pi?.let { am.cancel(it) }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val settings = PreferencesManager(context).loadSettings()
        if (!settings.timerEnabled) return

        when (intent.action) {
            ACTION_START_VPN -> {
                Log.d(TAG, "Timer: starting VPN")
                val servers = PreferencesManager(context).loadServers()
                val server = servers.firstOrNull { it.id == settings.selectedServerId }
                    ?: servers.firstOrNull() ?: return
                val vpnIntent = Intent(VpnService.ACTION_START).apply {
                    setClass(context, VpnService::class.java)
                    putExtra(VpnService.EXTRA_SERVER, server)
                }
                context.startForegroundService(vpnIntent)
            }
            ACTION_STOP_VPN -> {
                Log.d(TAG, "Timer: stopping VPN")
                context.startService(
                    Intent(VpnService.ACTION_STOP).apply {
                        setClass(context, VpnService::class.java)
                    }
                )
            }
        }
    }
}
