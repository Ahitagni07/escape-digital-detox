package com.example.escape

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * Best-effort restore after reboot. Android (and some OEM builds) may reject
 * foreground-service starts initiated by BOOT_COMPLETED. A rejection must not
 * crash ESCAPE or leave a misleading "running" status in app preferences.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "EscapeBootReceiver"
        private const val RESTART_CHANNEL = "escape_restart"
        private const val RESTART_NOTIFICATION_ID = 7003
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        // A delayed BOOT_COMPLETED may arrive after the user already started
        // ESCAPE manually. Never interfere with that live service.
        if (MonitorService.activeInstance != null) return

        val preferences = EscapePreferences(context)
        val shouldResume =
            preferences.getBoolean(EscapeKeys.RUNNING, false) ||
            preferences.getBoolean(EscapeKeys.LOCKED, false)
        if (!shouldResume) return

        try {
            val serviceIntent = Intent(context, MonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.i(TAG, "Requested protection restore after device reboot")
        } catch (error: IllegalStateException) {
            // Includes ForegroundServiceStartNotAllowedException (API 31+).
            handleStartRejected(context, preferences, error)
        } catch (error: SecurityException) {
            // Android may instead reject a service type / permission at launch.
            handleStartRejected(context, preferences, error)
        }
    }

    private fun handleStartRejected(
        context: Context,
        preferences: EscapePreferences,
        error: RuntimeException
    ) {
        Log.w(TAG, "OS refused ESCAPE foreground-service auto-start", error)

        // Avoid false claims that protection is active. Do not delete saved
        // selected apps, configured rules, downloaded Gemma, or earned access.
        if (MonitorService.activeInstance == null) {
            preferences.putBoolean(EscapeKeys.RUNNING, false)
            preferences.putBoolean(EscapeKeys.LOCKED, false)
            preferences.putBoolean(EscapeKeys.MISSION_ACTIVE, false)
        }

        notifyUserToRestart(context)
    }

    private fun notifyUserToRestart(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Notification permission unavailable; open ESCAPE manually")
            return
        }

        try {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        RESTART_CHANNEL,
                        "ESCAPE restart reminders",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Restart protection when Android prevents automatic recovery"
                    }
                )
            }

            val openEscape = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingOpen = PendingIntent.getActivity(
                context,
                7003,
                openEscape,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, RESTART_CHANNEL)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }

            manager.notify(
                RESTART_NOTIFICATION_ID,
                builder
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("Restart ESCAPE protection")
                    .setContentText("Android paused protection. Tap to open ESCAPE and press Start.")
                    .setContentIntent(pendingOpen)
                    .setAutoCancel(true)
                    .build()
            )
        } catch (error: RuntimeException) {
            // Even the fallback must not crash a BOOT_COMPLETED receiver.
            Log.w(TAG, "Unable to show restart notification", error)
        }
    }
}
