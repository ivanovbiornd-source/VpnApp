package com.vpnapp.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.vpnapp.service.SubscriptionWorker
import com.vpnapp.utils.NotificationHelper
import com.vpnapp.utils.PreferencesManager

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        NotificationHelper.createChannels(this)

        // Schedule background subscription worker
        val settings = PreferencesManager(this).loadSettings()
        if (settings.subscriptionUrl.isNotBlank()) {
            SubscriptionWorker.schedule(this, settings.autoUpdateInterval)
        }

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
