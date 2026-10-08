package com.example.escape

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val preferences = EscapePreferences(context)
        val shouldResume =
            preferences.getBoolean(EscapeKeys.RUNNING, false) ||
                preferences.getBoolean(EscapeKeys.LOCKED, false)

        if (!shouldResume) return

        val serviceIntent = Intent(context, MonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
