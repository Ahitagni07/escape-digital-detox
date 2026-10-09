package com.example.escape

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper

/** Opt-in, transient GPS distance accumulation. No route/coordinates persisted. */
class RideProgressTracker(private val context: Context, private val prefs: EscapePreferences) : LocationListener {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var previous: Location? = null
    private var tracking = false
    val meters: Int get() = prefs.getInt(EscapeKeys.RIDE_METERS, 0)

    fun start(): Boolean {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return false
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) return false
        return try {
            previous = null
            @Suppress("DEPRECATION")
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 8f, this, Looper.getMainLooper())
            tracking = true
            true
        } catch (_: SecurityException) { false } catch (_: IllegalArgumentException) { false }
    }

    fun stop() {
        if (tracking) manager.removeUpdates(this)
        tracking = false
        previous = null
    }

    override fun onLocationChanged(location: Location) {
        if (!tracking || !location.hasAccuracy() || location.accuracy > 35f) return
        val last = previous
        previous = location
        if (last == null) return
        val deltaSec = ((location.elapsedRealtimeNanos - last.elapsedRealtimeNanos) / 1e9).toFloat()
        if (deltaSec !in 2f..90f) return
        val deltaMeters = last.distanceTo(location)
        // Filter GPS jitter, big jumps, and implausibly fast vehicle travel.
        val speed = deltaMeters / deltaSec
        if (deltaMeters >= 6f && speed in 0.8f..11f) {
            prefs.putInt(EscapeKeys.RIDE_METERS, meters + deltaMeters.toInt())
        }
    }
}
