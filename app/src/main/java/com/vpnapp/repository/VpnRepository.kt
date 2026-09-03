package com.vpnapp.repository

import android.content.Context
import com.google.gson.Gson
import com.vpnapp.model.SubscriptionConfig
import com.vpnapp.model.VpnServer
import com.vpnapp.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class VpnRepository(private val context: Context) {

    private val prefs = PreferencesManager(context)
    private val gson = Gson()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchServers(url: String): Result<List<VpnServer>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val body = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response"))

            // Try parsing as SubscriptionConfig first, then as plain array
            val servers: List<VpnServer> = try {
                val config = gson.fromJson(body, SubscriptionConfig::class.java)
                config.servers
            } catch (e: Exception) {
                val type = object : com.google.gson.reflect.TypeToken<List<VpnServer>>() {}.type
                gson.fromJson(body, type)
            }

            prefs.saveServers(servers)
            Result.success(servers)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCachedServers(): List<VpnServer> = prefs.loadServers()

    fun needsUpdate(intervalHours: Int): Boolean {
        val last = prefs.getLastUpdateTime()
        if (last == 0L) return true
        val elapsed = System.currentTimeMillis() - last
        return elapsed > intervalHours * 3600 * 1000L
    }
}
