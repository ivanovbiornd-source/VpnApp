package com.vpnapp.service

import android.app.Service
import android.content.Intent
import android.net.VpnService as AndroidVpnService
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import com.vpnapp.model.VpnServer
import com.vpnapp.model.VpnState
import com.vpnapp.utils.NotificationHelper
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer

/**
 * VPN Service.
 *
 * NOTE: This is a DEMO VPN service implementation.
 * In a production app you would integrate a real VPN library such as:
 *  - WireGuard Android SDK (com.wireguard.android)
 *  - OpenVPN for Android (de.blinkt.openvpn)
 *  - sing-box / Xray SDK
 *
 * The current implementation establishes a local TUN interface
 * and demonstrates the full service lifecycle (start / stop / notification).
 * Real tunnel traffic routing requires the chosen VPN SDK.
 */
class VpnService : AndroidVpnService() {

    companion object {
        const val TAG = "VpnService"
        const val ACTION_START = "com.vpnapp.START_VPN"
        const val ACTION_STOP = "com.vpnapp.STOP_VPN"
        const val EXTRA_SERVER = "extra_server"

        // Broadcast for state changes
        const val BROADCAST_STATE = "com.vpnapp.VPN_STATE"
        const val EXTRA_STATE = "extra_state"
        const val EXTRA_MESSAGE = "extra_message"

        @Volatile var currentState: VpnState = VpnState.DISCONNECTED
        @Volatile var connectedServerName: String = ""
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var serviceJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val server = intent.getSerializableExtra(EXTRA_SERVER) as? VpnServer
                server?.let { startVpn(it) }
            }
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn(server: VpnServer) {
        updateState(VpnState.CONNECTING, "Подключение к ${server.name}…")

        NotificationHelper.createChannels(this)
        startForeground(
            NotificationHelper.NOTIF_ID_VPN,
            NotificationHelper.buildVpnNotification(this, "Подключение…", false)
        )

        serviceJob = scope.launch {
            try {
                // Build TUN interface
                val builder = Builder()
                    .setSession("VpnApp — ${server.name}")
                    .addAddress("10.0.0.2", 24)
                    .addDnsServer("1.1.1.1")
                    .addDnsServer("8.8.8.8")
                    .addRoute("0.0.0.0", 0)
                    .setMtu(1500)

                protect(Socket()) // ensure service socket is protected

                vpnInterface = builder.establish()

                if (vpnInterface == null) {
                    updateState(VpnState.ERROR, "Не удалось создать VPN интерфейс")
                    return@launch
                }

                // -------------------------------------------------------
                // DEMO: simulate a connection handshake delay.
                // Replace this block with real SDK tunnel setup:
                //   WireGuard: GoBackend.startTunnel(config, fd)
                //   OpenVPN:   OpenVPNThread.startOpenVPN(configFile)
                // -------------------------------------------------------
                delay(1500)

                connectedServerName = server.name
                updateState(VpnState.CONNECTED, "Подключено: ${server.name}")

                startForeground(
                    NotificationHelper.NOTIF_ID_VPN,
                    NotificationHelper.buildVpnNotification(
                        this@VpnService,
                        server.name,
                        true
                    )
                )

                // Keep alive loop (real SDK will block here instead)
                while (isActive && vpnInterface != null) {
                    delay(5000)
                }

            } catch (e: CancellationException) {
                Log.d(TAG, "VPN scope cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "VPN error: ${e.message}")
                updateState(VpnState.ERROR, "Ошибка: ${e.message}")
            }
        }
    }

    private fun stopVpn() {
        updateState(VpnState.DISCONNECTING, "Отключение…")
        serviceJob?.cancel()
        serviceJob = null

        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null
        connectedServerName = ""

        updateState(VpnState.DISCONNECTED, "Отключено")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateState(state: VpnState, message: String) {
        currentState = state
        sendBroadcast(Intent(BROADCAST_STATE).apply {
            putExtra(EXTRA_STATE, state.name)
            putExtra(EXTRA_MESSAGE, message)
        })
        Log.d(TAG, "State → $state: $message")
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        try { vpnInterface?.close() } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
