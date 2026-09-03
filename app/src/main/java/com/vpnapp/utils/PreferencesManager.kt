package com.vpnapp.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.vpnapp.model.AppSettings
import com.vpnapp.model.VpnServer

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_SETTINGS = "settings"
        private const val KEY_SERVERS = "servers_list"
        private const val KEY_LAST_UPDATE = "last_update"
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit().putString(KEY_SETTINGS, gson.toJson(settings)).apply()
    }

    fun loadSettings(): AppSettings {
        val json = prefs.getString(KEY_SETTINGS, null) ?: return AppSettings()
        return try {
            gson.fromJson(json, AppSettings::class.java)
        } catch (e: Exception) {
            AppSettings()
        }
    }

    fun saveServers(servers: List<VpnServer>) {
        val json = gson.toJson(servers)
        prefs.edit()
            .putString(KEY_SERVERS, json)
            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
            .apply()
    }

    fun loadServers(): List<VpnServer> {
        val json = prefs.getString(KEY_SERVERS, null) ?: return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<VpnServer>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getLastUpdateTime(): Long = prefs.getLong(KEY_LAST_UPDATE, 0)
}
