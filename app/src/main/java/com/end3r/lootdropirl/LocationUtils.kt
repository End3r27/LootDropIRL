package com.end3r.lootdropirl

import android.location.Location
import kotlin.math.*

object LocationUtils {

    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    fun isLocationValid(location: Location?): Boolean {
        return location != null &&
                location.latitude != 0.0 &&
                location.longitude != 0.0 &&
                location.accuracy < 100f // Accept locations with accuracy better than 100m
    }

    fun formatCoordinates(location: Location): String {
        return "${String.format("%.6f", location.latitude)}, ${String.format("%.6f", location.longitude)}"
    }

    fun formatDistance(meters: Float): String {
        return when {
            meters < 1000 -> "${meters.roundToInt()}m"
            else -> "${String.format("%.1f", meters / 1000)}km"
        }
    }
}
