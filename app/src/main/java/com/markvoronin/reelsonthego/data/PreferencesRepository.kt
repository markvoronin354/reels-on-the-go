package com.markvoronin.reelsonthego.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var isGlobalSwipeEnabled: Boolean
        get() = prefs.getBoolean(KEY_GLOBAL_SWIPE, false)
        set(value) = prefs.edit().putBoolean(KEY_GLOBAL_SWIPE, value).apply()

    var enabledPackages: Set<String>
        get() = prefs.getStringSet(KEY_ENABLED_PACKAGES, DEFAULT_PACKAGES) ?: DEFAULT_PACKAGES
        set(value) = prefs.edit().putStringSet(KEY_ENABLED_PACKAGES, value).apply()

    fun isPackageEnabled(packageName: String): Boolean {
        if (isGlobalSwipeEnabled) return true
        return enabledPackages.contains(packageName)
    }

    fun togglePackage(packageName: String, enabled: Boolean) {
        val current = enabledPackages.toMutableSet()
        if (enabled) {
            current.add(packageName)
        } else {
            current.remove(packageName)
        }
        enabledPackages = current
    }

    companion object {
        private const val PREFS_NAME = "reels_control_prefs"
        private const val KEY_SERVICE_ENABLED = "key_service_enabled"
        private const val KEY_GLOBAL_SWIPE = "key_global_swipe"
        private const val KEY_ENABLED_PACKAGES = "key_enabled_packages"

        val DEFAULT_PACKAGES = setOf(
            "com.instagram.android",        // Instagram
            "com.facebook.katana",          // Facebook
            "com.facebook.lite",            // Facebook Lite
            "com.zhiliaoapp.musically",     // TikTok (Global)
            "com.ss.android.ugc.trill",      // TikTok (Regional)
            "com.google.android.youtube",   // YouTube / Shorts
            "com.snapchat.android"          // Snapchat
        )

        val SUPPORTED_APPS = listOf(
            SupportedApp("Instagram", "com.instagram.android"),
            SupportedApp("Facebook", "com.facebook.katana"),
            SupportedApp("Facebook Lite", "com.facebook.lite"),
            SupportedApp("TikTok", "com.zhiliaoapp.musically"),
            SupportedApp("TikTok (Alt)", "com.ss.android.ugc.trill"),
            SupportedApp("YouTube / Shorts", "com.google.android.youtube"),
            SupportedApp("Snapchat", "com.snapchat.android")
        )
    }
}

data class SupportedApp(
    val displayName: String,
    val packageName: String
)
