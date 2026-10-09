package com.example.escape

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.*

/**
 * Discover cycleway segments and green stops from OpenStreetMap (Overpass).
 * Discovery needs network; the *last* search remains available offline.
 * Results are NOT navigable routes. We delegate cycling directions to a map app.
 * No coordinates are shared with Gemma or any ESCAPE server.
 */
class WeekendExplorer(private val context: Context) {
    private val cache = context.getSharedPreferences("escape_weekend_places", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())

    fun savedPlaces(): Map<String, Any> = cached(null)

    fun hasLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun discover(forceRefresh: Boolean, deliver: (Map<String, Any>) -> Unit) {
        if (!hasLocationPermission()) {
            deliver(mapOf("error" to "Location permission needed to search near you.", "places" to emptyList<Any>()))
            return
        }
        currentLocation { loc ->
            if (loc == null) {
                deliver(cached("GPS/location unavailable. Showing saved places if available."))
                return@currentLocation
            }
            val savedAt = cache.getLong("updated", 0L)
            val oldLat = cache.getString("lat", "")?.toDoubleOrNull()
            val oldLon = cache.getString("lon", "")?.toDoubleOrNull()
            val recent = System.currentTimeMillis() - savedAt < 7 * 24 * 3600_000L
            val close = oldLat != null && oldLon != null &&
                distanceMeters(loc.latitude, loc.longitude, oldLat, oldLon) < 2500
            if (!forceRefresh && recent && close && cache.getString("places", null) != null) {
                deliver(cached(null))
                return@currentLocation
            }
            Thread({
                try {
                    val places = queryOverpass(loc.latitude, loc.longitude)
                    val json = JSONArray()
                    places.forEach { json.put(JSONObject(it)) }
                    cache.edit()
                        .putString("places", json.toString())
                        .putString("lat", loc.latitude.toString())
                        .putString("lon", loc.longitude.toString())
                        .putLong("updated", System.currentTimeMillis()).apply()
                    handler.post {
                        deliver(mapOf(
                            "places" to places,
                            "cached" to false,
                            "originLat" to loc.latitude,
                            "originLon" to loc.longitude,
                            "message" to "Nearby mapped cycleways and green spaces. Distances are straight-line estimates."
                        ))
                    }
                } catch (e: Exception) {
                    handler.post { deliver(cached("Could not refresh OSM: ${e.message}. Showing saved places if available.")) }
                }
            }, "escape-weekend-osm").start()
        }
    }

    /** Uses GPS/network location services; GPS itself does not require internet. */
    private fun currentLocation(callback: (Location?) -> Unit) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val candidates = if (fine) listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                         else listOf(LocationManager.NETWORK_PROVIDER)
        val enabled = candidates.filter { try { lm.isProviderEnabled(it) } catch (_: Exception) { false } }
        if (enabled.isEmpty()) { callback(null); return }

        val last = enabled.mapNotNull { provider ->
            try { lm.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
        }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time in 0..(30 * 60_000L)) {
            callback(last)
            return
        }

        // Request a fresh fix; avoid blocking the UI and always return within 12 seconds.
        var done = false
        val provider = if (fine && enabled.contains(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else enabled.first()
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (done) return
                done = true
                try { lm.removeUpdates(this) } catch (_: SecurityException) {}
                callback(location)
            }
        }
        val timeout = Runnable {
            if (!done) {
                done = true
                try { lm.removeUpdates(listener) } catch (_: SecurityException) {}
                callback(last)
            }
        }
        try {
            @Suppress("DEPRECATION")
            lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            handler.postDelayed(timeout, 12_000L)
        } catch (_: Exception) { callback(last) }
    }

    private fun queryOverpass(lat: Double, lon: Double): List<Map<String, Any>> {
        // Deliberately bounded to minimize load on public Overpass infrastructure.
        val query = """
            [out:json][timeout:20];
            (
              way(around:4500,$lat,$lon)["highway"="cycleway"];
              way(around:4500,$lat,$lon)["cycleway"~"^(track|lane|opposite_track)$"];
            );
            out center 80;
            (
              nwr(around:4500,$lat,$lon)["leisure"~"^(park|garden|nature_reserve)$"];
              nwr(around:4500,$lat,$lon)["landuse"="grass"];
            );
            out center 80;
        """.trimIndent()
        val connection = URL("https://overpass.kumi.systems/api/interpreter").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 12_000
            connection.readTimeout = 26_000
            connection.setRequestProperty("User-Agent", "ESCAPE-DigitalDetox/0.9 (non-commercial weekend discovery)")
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.doOutput = true
            val body = "data=" + java.net.URLEncoder.encode(query, "UTF-8")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode != 200) throw IllegalStateException("Overpass HTTP ${connection.responseCode}")
            val text = connection.inputStream.bufferedReader().use { reader ->
                val output = StringBuilder()
                val chunk = CharArray(8192)
                while (true) {
                    val count = reader.read(chunk)
                    if (count < 0) break
                    output.append(chunk, 0, count)
                    if (output.length > 1_000_000) throw IllegalStateException("Response too large; try later")
                }
                output.toString()
            }
            val elements = JSONObject(text).optJSONArray("elements") ?: JSONArray()
            val entries = mutableListOf<Map<String, Any>>()
            for (i in 0 until elements.length()) {
                val item = elements.optJSONObject(i) ?: continue
                val tags = item.optJSONObject("tags") ?: continue
                val center = item.optJSONObject("center") ?: item
                if (!center.has("lat") || !center.has("lon")) continue
                val pLat = center.optDouble("lat")
                val pLon = center.optDouble("lon")
                if (pLat.isNaN() || pLon.isNaN()) continue
                val d = distanceMeters(lat, lon, pLat, pLon)
                if (d > 4800 || d < 70) continue
                val cycle = tags.optString("highway") == "cycleway" || tags.has("cycleway")
                val type = if (cycle) "cycleway" else "green"
                val name = tags.optString("name").take(75)
                val kind = when {
                    cycle -> "Mapped cycleway segment"
                    tags.optString("landuse") == "grass" -> "Grass area"
                    tags.optString("leisure") == "garden" -> "Garden"
                    tags.optString("leisure") == "nature_reserve" -> "Nature reserve"
                    else -> "Park / green space"
                }
                entries.add(mapOf(
                    "id" to "${item.optString("type")}-${item.optLong("id")}",
                    "name" to (if (name.isNotBlank()) name else kind),
                    "kind" to kind,
                    "type" to type,
                    "latitude" to pLat,
                    "longitude" to pLon,
                    "distanceMeters" to d.roundToInt()
                ))
            }
            // Spread destinations geographically, avoid showing several points on same segment.
            val selected = mutableListOf<Map<String, Any>>()
            for (type in listOf("cycleway", "green")) {
                for (candidate in entries.filter { it["type"] == type }.sortedBy { it["distanceMeters"] as Int }) {
                    if (selected.count { it["type"] == type } >= 4) break
                    val pLat = candidate["latitude"] as Double
                    val pLon = candidate["longitude"] as Double
                    if (selected.any { it["type"] == type &&
                        distanceMeters(it["latitude"] as Double, it["longitude"] as Double, pLat, pLon) < 450 }) continue
                    selected.add(candidate)
                }
            }
            return selected
        } finally { connection.disconnect() }
    }

    private fun cached(warning: String?): Map<String, Any> {
        val array = try { JSONArray(cache.getString("places", "[]")) } catch (_: Exception) { JSONArray() }
        val items = mutableListOf<Map<String, Any>>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            items.add(mapOf(
                "id" to obj.optString("id"), "name" to obj.optString("name"),
                "type" to obj.optString("type"), "kind" to obj.optString("kind"),
                "latitude" to obj.optDouble("latitude"), "longitude" to obj.optDouble("longitude"),
                "distanceMeters" to obj.optInt("distanceMeters")
            ))
        }
        return mapOf(
            "places" to items,
            "cached" to true,
            "originLat" to (cache.getString("lat", "0")?.toDoubleOrNull() ?: 0.0),
            "originLon" to (cache.getString("lon", "0")?.toDoubleOrNull() ?: 0.0),
            "message" to (warning ?: "Saved nearby places — available offline. Recheck before cycling.")
        )
    }

    fun openBicycleDirections(destinationLat: Double, destinationLon: Double) {
        require(destinationLat in -90.0..90.0 && destinationLon in -180.0..180.0)
        val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${String.format(Locale.US,"%.6f",destinationLat)}%2C${String.format(Locale.US,"%.6f",destinationLon)}&travelmode=bicycling")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earth = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        return earth * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
