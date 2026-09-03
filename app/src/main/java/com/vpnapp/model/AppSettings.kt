package com.vpnapp.model

data class AppSettings(
    var subscriptionUrl: String = "",
    var selectedServerId: String = "",
    var timerEnabled: Boolean = false,
    var timerStartHour: Int = 9,
    var timerStartMinute: Int = 0,
    var timerStopHour: Int = 18,
    var timerStopMinute: Int = 0,
    var gpsAutoEnabled: Boolean = false,
    var homeLatitude: Double = 0.0,
    var homeLongitude: Double = 0.0,
    var homeRadius: Int = 100,       // metres
    var autoUpdateInterval: Int = 24  // hours
)
