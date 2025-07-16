package com.end3r.lootdropirl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task

class LocationTracker(
    private val context: Context,
    private val viewModel: MainViewModel
) {

    private var fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var locationCallback: LocationCallback? = null
    private var locationRequest: LocationRequest? = null
    private var isTrackingLocation = false

    companion object {
        private const val TAG = "LocationTracker"
        private const val LOCATION_UPDATE_INTERVAL = 5000L // 5 seconds
        private const val LOCATION_FASTEST_INTERVAL = 2000L // 2 seconds
    }

    fun startLocationUpdates() {
        if (isTrackingLocation) return

        if (!hasLocationPermission()) {
            Log.e(TAG, "Location permission not granted")
            return
        }

        createLocationRequest()
        createLocationCallback()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest!!,
                locationCallback!!,
                Looper.getMainLooper()
            )
            isTrackingLocation = true
            Log.d(TAG, "Location updates started")

            // Get last known location immediately
            getLastKnownLocation()

        } catch (e: SecurityException) {
            Log.e(TAG, "Lost location permission. Could not request updates.", e)
        }
    }

    fun stopLocationUpdates() {
        if (!isTrackingLocation) return

        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
            isTrackingLocation = false
            Log.d(TAG, "Location updates stopped")
        }
    }

    private fun createLocationRequest() {
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_UPDATE_INTERVAL
        ).apply {
            setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL)
            setMaxUpdateDelayMillis(LOCATION_UPDATE_INTERVAL * 2)
        }.build()
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)

                for (location in locationResult.locations) {
                    Log.d(TAG, "Location update: ${location.latitude}, ${location.longitude}")
                    viewModel.updateLocation(location)
                }
            }

            override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                super.onLocationAvailability(locationAvailability)

                if (!locationAvailability.isLocationAvailable) {
                    Log.w(TAG, "Location not available")
                    viewModel.updateLocationStatus(false)
                } else {
                    Log.d(TAG, "Location available")
                    viewModel.updateLocationStatus(true)
                }
            }
        }
    }

    private fun getLastKnownLocation() {
        try {
            val lastLocationTask: Task<Location> = fusedLocationClient.lastLocation
            lastLocationTask.addOnSuccessListener { location ->
                if (location != null) {
                    Log.d(TAG, "Last known location: ${location.latitude}, ${location.longitude}")
                    viewModel.updateLocation(location)
                } else {
                    Log.d(TAG, "No last known location available")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Lost location permission", e)
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }
}
