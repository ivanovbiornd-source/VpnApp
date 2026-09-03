package com.vpnapp.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.vpnapp.R
import com.vpnapp.databinding.ActivityMainBinding
import com.vpnapp.model.VpnState
import com.vpnapp.service.LocationService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private var pulseAnimator: ObjectAnimator? = null

    // VPN permission launcher
    private val vpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.startVpn(this)
        } else {
            Toast.makeText(this, "Разрешение VPN не предоставлено", Toast.LENGTH_SHORT).show()
        }
    }

    // Location permission launcher
    private val locationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startLocationService()
        } else {
            Toast.makeText(this, "GPS-автоподключение требует разрешения геолокации", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupObservers()
        setupListeners()
    }

    private fun setupObservers() {
        viewModel.vpnState.observe(this) { state ->
            updateUiForState(state)
        }

        viewModel.statusMessage.observe(this) { msg ->
            binding.tvStatus.text = msg
        }

        viewModel.selectedServer.observe(this) { server ->
            if (server != null) {
                binding.tvSelectedServer.text = "${server.flag} ${server.name}"
                binding.tvServerCountry.text = server.country
            } else {
                binding.tvSelectedServer.text = "Сервер не выбран"
                binding.tvServerCountry.text = "Добавьте серверы через настройки"
            }
        }

        viewModel.updateResult.observe(this) { msg ->
            msg?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun setupListeners() {
        // Big connect button
        binding.btnConnect.setOnClickListener {
            val state = viewModel.vpnState.value ?: VpnState.DISCONNECTED
            when (state) {
                VpnState.DISCONNECTED, VpnState.ERROR -> requestVpnPermissionAndConnect()
                VpnState.CONNECTED -> viewModel.stopVpn(this)
                VpnState.CONNECTING, VpnState.DISCONNECTING -> { /* wait */ }
            }
        }

        // Server selection
        binding.cardServer.setOnClickListener {
            startActivity(Intent(this, ServersActivity::class.java))
        }

        // Refresh servers
        binding.btnRefresh.setOnClickListener {
            viewModel.fetchServers()
            Toast.makeText(this, "Обновление серверов…", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestVpnPermissionAndConnect() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnLauncher.launch(intent)
        } else {
            viewModel.startVpn(this)
        }
    }

    private fun updateUiForState(state: VpnState) {
        when (state) {
            VpnState.DISCONNECTED -> {
                binding.btnConnect.setImageResource(R.drawable.ic_power_off)
                binding.btnConnect.backgroundTintList =
                    ContextCompat.getColorStateList(this, R.color.btn_disconnected)
                binding.connectRing.setBackgroundResource(R.drawable.ring_disconnected)
                stopPulse()
            }
            VpnState.CONNECTING, VpnState.DISCONNECTING -> {
                binding.btnConnect.setImageResource(R.drawable.ic_power_off)
                binding.btnConnect.backgroundTintList =
                    ContextCompat.getColorStateList(this, R.color.btn_connecting)
                startPulse()
            }
            VpnState.CONNECTED -> {
                binding.btnConnect.setImageResource(R.drawable.ic_power_on)
                binding.btnConnect.backgroundTintList =
                    ContextCompat.getColorStateList(this, R.color.btn_connected)
                binding.connectRing.setBackgroundResource(R.drawable.ring_connected)
                stopPulse()
            }
            VpnState.ERROR -> {
                binding.btnConnect.setImageResource(R.drawable.ic_power_off)
                binding.btnConnect.backgroundTintList =
                    ContextCompat.getColorStateList(this, R.color.btn_error)
                binding.connectRing.setBackgroundResource(R.drawable.ring_disconnected)
                stopPulse()
            }
        }
    }

    private fun startPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = ObjectAnimator.ofFloat(binding.connectRing, View.ALPHA, 0.3f, 1f).apply {
            duration = 800
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        binding.connectRing.alpha = 1f
    }

    fun startLocationService() {
        val intent = Intent(LocationService.ACTION_START).apply {
            setClass(this@MainActivity, LocationService::class.java)
        }
        startForegroundService(intent)
    }

    fun checkAndRequestLocationPermission() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            perms.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startLocationService()
        } else {
            locationPermLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
