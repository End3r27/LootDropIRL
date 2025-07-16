package com.end3r.lootdropirl

import android.location.Location
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlin.math.roundToInt

class MainViewModel : ViewModel() {

    // Location data
    val currentLocation = MutableLiveData<Location>()
    val locationStatus = MutableLiveData<Boolean>()
    val shouldFollowUser = MutableLiveData<Boolean>()

    // Movement tracking
    val totalDistance = MutableLiveData<Float>()
    val distanceFromLastDrop = MutableLiveData<Float>()

    private var lastLocation: Location? = null
    private var lastDropLocation: Location? = null
    private var cumulativeDistance: Float = 0f
    private var distanceFromDrop: Float = 0f

    init {
        // Initialize values
        totalDistance.value = 0f
        distanceFromLastDrop.value = 0f
        shouldFollowUser.value = true
        locationStatus.value = false
    }

    fun updateLocation(location: Location) {
        // Calculate distance traveled
        lastLocation?.let { prevLocation ->
            val distance = prevLocation.distanceTo(location)
            if (distance > 0) {
                cumulativeDistance += distance
                totalDistance.value = cumulativeDistance

                // Update distance from last drop
                lastDropLocation?.let { dropLocation ->
                    distanceFromDrop = dropLocation.distanceTo(location)
                    distanceFromLastDrop.value = distanceFromDrop
                }
            }
        }

        // Update current location
        currentLocation.value = location
        lastLocation = location

        // If this is the first location, set as drop location
        if (lastDropLocation == null) {
            lastDropLocation = location
        }
    }

    fun updateLocationStatus(isAvailable: Boolean) {
        locationStatus.value = isAvailable
    }

    fun resetDistanceTracking() {
        cumulativeDistance = 0f
        distanceFromDrop = 0f
        totalDistance.value = 0f
        distanceFromLastDrop.value = 0f
        lastDropLocation = currentLocation.value
    }

    fun getFormattedLocation(): String {
        return currentLocation.value?.let { location ->
            "Lat: ${String.format("%.6f", location.latitude)}, " +
                    "Lng: ${String.format("%.6f", location.longitude)}"
        } ?: "No location available"
    }

    fun getFormattedDistance(): String {
        val distance = totalDistance.value ?: 0f
        return when {
            distance < 1000 -> "${distance.roundToInt()}m"
            else -> "${String.format("%.1f", distance / 1000)}km"
        }
    }

    fun getFormattedDistanceFromDrop(): String {
        val distance = distanceFromLastDrop.value ?: 0f
        return "${distance.roundToInt()}m from last drop"
    }
}
