package com.vpnapp.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.vpnapp.R
import com.vpnapp.ui.MainActivity

object NotificationHelper {

    const val CHANNEL_VPN = "vpn_channel"
    const val CHANNEL_GENERAL = "general_channel"
    const val NOTIF_ID_VPN = 1001
    const val NOTIF_ID_GENERAL = 1002

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val vpnChannel = NotificationChannel(
            CHANNEL_VPN,
            "VPN статус",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Уведомления о статусе VPN подключения"
            setShowBadge(false)
        }

        val generalChannel = NotificationChannel(
            CHANNEL_GENERAL,
            "Общие уведомления",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Системные уведомления приложения"
        }

        manager.createNotificationChannel(vpnChannel)
        manager.createNotificationChannel(generalChannel)
    }

    fun buildVpnNotification(context: Context, status: String, isConnected: Boolean): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_VPN)
            .setContentTitle(if (isConnected) "VPN подключён" else "VPN отключён")
            .setContentText(status)
            .setSmallIcon(if (isConnected) R.drawable.ic_vpn_on else R.drawable.ic_vpn_off)
            .setContentIntent(pi)
            .setOngoing(isConnected)
            .setSilent(true)
            .build()
    }

    fun showNotification(context: Context, title: String, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_GENERAL)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_vpn_off)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIF_ID_GENERAL, notification)
    }
}
