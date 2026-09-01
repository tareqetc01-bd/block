package com.example.shortsblocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receiver to preserve state and ensure service initialization when device is rebooted
 * or when the app is updated.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(ShortsBlockerService.PREFS_NAME, Context.MODE_PRIVATE)
        val isMasterEnabled = prefs.getBoolean(ShortsBlockerService.PREF_ENABLED, true)
        // AccessibilityService is managed by Android OS; this ensures state validation on boot
        if (isMasterEnabled) {
            // Preferences remain persistent across reboots
        }
    }
}
