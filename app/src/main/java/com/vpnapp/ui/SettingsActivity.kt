package com.vpnapp.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.vpnapp.databinding.ActivitySettingsBinding
import com.vpnapp.service.AlarmReceiver
import com.vpnapp.service.LocationService
import com.vpnapp.service.SubscriptionWorker
import com.vpnapp.model.AppSettings

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Настройки"

        loadCurrentSettings()
        setupListeners()
    }

    private fun loadCurrentSettings() {
        val s = viewModel.getCurrentSettings()
        binding.etSubscriptionUrl.setText(s.subscriptionUrl)
        binding.switchTimer.isChecked = s.timerEnabled
        binding.tpStart.hour = s.timerStartHour
        binding.tpStart.minute = s.timerStartMinute
        binding.tpStop.hour = s.timerStopHour
        binding.tpStop.minute = s.timerStopMinute
        binding.switchGps.isChecked = s.gpsAutoEnabled
        updateTimerGroupVisibility(s.timerEnabled)
        updateGpsGroupVisibility(s.gpsAutoEnabled)
    }

    private fun setupListeners() {
        binding.switchTimer.setOnCheckedChangeListener { _, checked ->
            updateTimerGroupVisibility(checked)
        }

        binding.switchGps.setOnCheckedChangeListener { _, checked ->
            updateGpsGroupVisibility(checked)
            if (checked) {
                (this as? MainActivity)?.checkAndRequestLocationPermission()
                    ?: requestLocationViaMain()
            } else {
                stopService(Intent(LocationService.ACTION_STOP).apply {
                    setClass(this@SettingsActivity, LocationService::class.java)
                })
            }
        }

        binding.btnSaveHome.setOnClickListener {
            saveCurrentLocationAsHome()
        }

        binding.btnSave.setOnClickListener {
            saveSettings()
        }
    }

    private fun updateTimerGroupVisibility(show: Boolean) {
        val vis = if (show) android.view.View.VISIBLE else android.view.View.GONE
        binding.groupTimer.visibility = vis
    }

    private fun updateGpsGroupVisibility(show: Boolean) {
        val vis = if (show) android.view.View.VISIBLE else android.view.View.GONE
        binding.groupGps.visibility = vis
    }

    private fun saveCurrentLocationAsHome() {
        // We use FusedLocationProviderClient to get current location
        val fusedClient = com.google.android.gms.location.LocationServices
            .getFusedLocationProviderClient(this)
        try {
            fusedClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val s = viewModel.getCurrentSettings().copy(
                        homeLatitude = location.latitude,
                        homeLongitude = location.longitude
                    )
                    viewModel.saveSettings(s)
                    Toast.makeText(
                        this,
                        "Дом сохранён: ${String.format("%.5f", location.latitude)}, ${String.format("%.5f", location.longitude)}",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this, "Не удалось получить геолокацию", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Требуется разрешение геолокации", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveSettings() {
        val current = viewModel.getCurrentSettings()
        val updated = current.copy(
            subscriptionUrl = binding.etSubscriptionUrl.text.toString().trim(),
            timerEnabled = binding.switchTimer.isChecked,
            timerStartHour = binding.tpStart.hour,
            timerStartMinute = binding.tpStart.minute,
            timerStopHour = binding.tpStop.hour,
            timerStopMinute = binding.tpStop.minute,
            gpsAutoEnabled = binding.switchGps.isChecked
        )
        viewModel.saveSettings(updated)

        // Apply timer
        if (updated.timerEnabled) {
            AlarmReceiver.scheduleDaily(this, updated)
        } else {
            AlarmReceiver.cancel(this)
        }

        // Schedule/cancel subscription worker
        if (updated.subscriptionUrl.isNotBlank()) {
            SubscriptionWorker.schedule(this, updated.autoUpdateInterval)
        } else {
            SubscriptionWorker.cancelAll(this)
        }

        Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun requestLocationViaMain() {
        Toast.makeText(this, "Перейдите на главный экран для выдачи разрешений GPS", Toast.LENGTH_LONG).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
