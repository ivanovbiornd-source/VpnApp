# VpnApp ProGuard Rules

# Keep model classes for Gson serialization
-keep class com.vpnapp.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Gson
-keep class com.google.gson.** { *; }

# WorkManager
-keep class androidx.work.** { *; }

# Google Play Services Location
-keep class com.google.android.gms.location.** { *; }
