package com.example.escape

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build

class NotificationHelper(
    private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "escape_monitor"
        const val MISSION_CHANNEL_ID = "escape_missions"
        const val NOTIFICATION_ID = 7001
        const val MISSION_NOTIFICATION_ID = 7002
    }

    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "ESCAPE protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps ESCAPE active while protecting selected apps."
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                MISSION_CHANNEL_ID,
                "ESCAPE missions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Offline mission reminders generated locally on your phone."
            }
        )
    }

    fun build(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        return builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("ESCAPE is active")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(appPendingIntent())
            .build()
    }

    fun update(text: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, build(text))
    }

    fun showMissionReminder(
        mission: EscapeMission,
        evening: Boolean
    ) {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, MISSION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val title = if (evening) {
            "🌙 Screen-free mission ready"
        } else {
            "🌱 Earn your scroll"
        }

        val notification = builder
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(title)
            .setContentText("${mission.title}: ${mission.instruction}")
            .setStyle(
                Notification.BigTextStyle()
                    .bigText("${mission.title}\n\n${mission.instruction}\n\nOpen ESCAPE to complete this challenge.")
            )
            .setAutoCancel(true)
            .setContentIntent(appPendingIntent())
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(MISSION_NOTIFICATION_ID, notification)
    }

    fun showAccessGranted(minutes: Int) {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, MISSION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("✅ Social access earned")
            .setContentText("Your protected apps are available for $minutes minutes.")
            .setAutoCancel(true)
            .setContentIntent(appPendingIntent())
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(MISSION_NOTIFICATION_ID, notification)
    }

    fun cancelMissionReminder() {
        context.getSystemService(NotificationManager::class.java)
            .cancel(MISSION_NOTIFICATION_ID)
    }

    private fun appPendingIntent(): PendingIntent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return null

        return PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
