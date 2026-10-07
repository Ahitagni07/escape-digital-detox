package com.example.escape

import android.content.Context
import android.content.pm.PackageManager

class AppQueryHelper(
    private val context: Context
) {
    @Suppress("DEPRECATION")
    private fun isInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun installedSupportedApps(): List<Map<String, Any>> {
        return SupportedApps.all
            .filter { isInstalled(it.packageName) }
            .map {
                mapOf(
                    "name" to it.name,
                    "packageName" to it.packageName,
                    "browser" to it.browser
                )
            }
    }
}
