package com.vpnapp.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.vpnapp.model.AppSettings
import com.vpnapp.model.VpnServer
import com.vpnapp.model.VpnState
import com.vpnapp.repository.VpnRepository
import com.vpnapp.service.VpnService
import com.vpnapp.utils.PreferencesManager
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    private val repo = VpnRepository(application)

    private val _vpnState = MutableLiveData(VpnState.DISCONNECTED)
    val vpnState: LiveData<VpnState> = _vpnState

    private val _statusMessage = MutableLiveData("Нажмите для подключения")
    val statusMessage: LiveData<String> = _statusMessage

    private val _servers = MutableLiveData<List<VpnServer>>(emptyList())
    val servers: LiveData<List<VpnServer>> = _servers

    private val _settings = MutableLiveData(prefs.loadSettings())
    val settings: LiveData<AppSettings> = _settings

    private val _selectedServer = MutableLiveData<VpnServer?>()
    val selectedServer: LiveData<VpnServer?> = _selectedServer

    private val _updateResult = MutableLiveData<String?>()
    val updateResult: LiveData<String?> = _updateResult

    // BroadcastReceiver to get VPN state from service
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val stateName = intent.getStringExtra(VpnService.EXTRA_STATE) ?: return
            val msg = intent.getStringExtra(VpnService.EXTRA_MESSAGE) ?: ""
            _vpnState.postValue(VpnState.valueOf(stateName))
            _statusMessage.postValue(msg)
        }
    }

    init {
        application.registerReceiver(
            stateReceiver,
            IntentFilter(VpnService.BROADCAST_STATE),
            Context.RECEIVER_NOT_EXPORTED
        )
        loadCachedData()
        // Sync with currently running service
        _vpnState.value = VpnService.currentState
    }

    private fun loadCachedData() {
        val cachedServers = repo.getCachedServers()
        _servers.value = cachedServers
        val settings = prefs.loadSettings()
        _settings.value = settings
        _selectedServer.value = cachedServers.firstOrNull { it.id == settings.selectedServerId }
            ?: cachedServers.firstOrNull()
    }

    fun fetchServers(url: String? = null) {
        val targetUrl = url ?: prefs.loadSettings().subscriptionUrl
        if (targetUrl.isBlank()) {
            _updateResult.value = "Введите URL подписки в настройках"
            return
        }
        viewModelScope.launch {
            _updateResult.value = null
            val result = repo.fetchServers(targetUrl)
            if (result.isSuccess) {
                val list = result.getOrNull() ?: emptyList()
                _servers.postValue(list)
                _updateResult.postValue("Загружено ${list.size} серверов")
                // Update selected server reference
                val settings = prefs.loadSettings()
                _selectedServer.postValue(
                    list.firstOrNull { it.id == settings.selectedServerId } ?: list.firstOrNull()
                )
            } else {
                _updateResult.postValue("Ошибка: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun selectServer(server: VpnServer) {
        _selectedServer.value = server
        val s = prefs.loadSettings().copy(selectedServerId = server.id)
        prefs.saveSettings(s)
        _settings.value = s
    }

    fun saveSettings(settings: AppSettings) {
        prefs.saveSettings(settings)
        _settings.value = settings
    }

    fun getCurrentSettings(): AppSettings = prefs.loadSettings()

    fun startVpn(context: Context) {
        val server = _selectedServer.value
        if (server == null) {
            _statusMessage.value = "Сначала выберите сервер"
            return
        }
        val intent = Intent(VpnService.ACTION_START).apply {
            setClass(context, VpnService::class.java)
            putExtra(VpnService.EXTRA_SERVER, server)
        }
        context.startForegroundService(intent)
    }

    fun stopVpn(context: Context) {
        context.startService(
            Intent(VpnService.ACTION_STOP).apply {
                setClass(context, VpnService::class.java)
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(stateReceiver)
        } catch (_: Exception) {}
    }
}
