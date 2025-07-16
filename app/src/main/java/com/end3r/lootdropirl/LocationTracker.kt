package com.end3r.lootdropirl // Ensure this package is correct

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.DefaultLifecycleObserver // For lifecycle-aware example
import androidx.lifecycle.LifecycleOwner // Import this
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task

class LocationTracker(
    private val context: Context,
    private val viewModel: MainViewModel,
    private val lifecycleOwner: LifecycleOwner // Added lifecycleOwner parameter
) {

    private var fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var locationCallback: LocationCallback? = null
    private var isTrackingLocation = false

    // Consider initializing LocationRequest directly as its parameters seem fixed
    private val locationRequest: LocationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        LOCATION_UPDATE_INTERVAL
    ).apply {
        setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL)
        setMaxUpdateDelayMillis(LOCATION_UPDATE_INTERVAL * 2) // Optional: for more flexibility on updates
    }.build()

    companion object {
        private const val TAG = "LocationTracker"
        private const val LOCATION_UPDATE_INTERVAL = 5000L // 5 seconds
        private const val LOCATION_FASTEST_INTERVAL = 2000L // 2 seconds
    }

    // Optional: Make LocationTracker lifecycle-aware
    // init {
    //     lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
    //         override fun onResume(owner: LifecycleOwner) {
    //             // If you have logic to resume tracking (e.g., if it was paused and permissions are still granted)
    //             // if (wasTrackingAndPermissionGranted) { // You'd need a flag for this
    //             //     startLocationUpdates()
    //             // }
    //         }

    //         override fun onPause(owner: LifecycleOwner) {
    //             // Stop updates when the lifecycle owner is paused to save battery
    //             // stopLocationUpdates()
    //         }

    //         // You could also use onDestroy to ensure everything is cleaned up,
    //         // though for location updates, onPause is often sufficient.
    //     })
    // }


    fun startLocationUpdates() {
        if (isTrackingLocation) return

        if (!hasLocationPermission()) {
            Log.e(TAG, "Location permission not granted")
            // Optionally, inform the ViewModel or show a message
            return
        }

        // createLocationRequest() // No longer needed if locationRequest is a val initialized above
        createLocationCallback() // Ensure callback is (re)created if needed

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest, // Use the val directly
                locationCallback!!, // locationCallback should be non-null after createLocationCallback()
                Looper.getMainLooper()
            )
            isTrackingLocation = true
            Log.d(TAG, "Location updates started")

            // Get last known location immediately
            getLastKnownLocation()

        } catch (e: SecurityException) {
            Log.e(TAG, "Lost location permission. Could not request updates.", e)
            isTrackingLocation = false // Ensure tracking state is accurate
        }
    }

    fun stopLocationUpdates() {
        if (!isTrackingLocation) return // Only stop if currently tracking

        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
            // No need to null out locationCallback if it's always recreated in createLocationCallback
        }
        isTrackingLocation = false
        Log.d(TAG, "Location updates stopped")
    }

    // This method is no longer needed if locationRequest is a val initialized at the class level.
    // private fun createLocationRequest() {
    // locationRequest = LocationRequest.Builder(
    // Priority.PRIORITY_HIGH_ACCURACY,
    // LOCATION_UPDATE_INTERVAL
    // ).apply {
    // setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL)
    // setMaxUpdateDelayMillis(LOCATION_UPDATE_INTERVAL * 2)
    // }.build()
    // }

    private fun createLocationCallback() {
        // Ensure you're not creating multiple callbacks if one already exists and is suitable
        if (locationCallback == null) {
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    // super.onLocationResult(locationResult) // Not strictly necessary unless you need its base behavior
                    for (location in locationResult.locations) { // Iterate as there might be batched locations
                        Log.d(TAG, "Location update: ${location.latitude}, ${location.longitude}")
                        viewModel.updateLocation(location) // Call your ViewModel method
                    }
                }

                override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                    // super.onLocationAvailability(locationAvailability) // Not strictly necessary
                    if (!locationAvailability.isLocationAvailable) {
                        Log.w(TAG, "Location not available")
                        viewModel.updateLocationStatus(false) // Call your ViewModel method
                    } else {
                        Log.d(TAG, "Location available")
                        viewModel.updateLocationStatus(true) // Call your ViewModel method
                    }
                }
            }
        }
    }

    private fun getLastKnownLocation() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Cannot get last known location without permission.")
            return
        }
        try {
            val lastLocationTask: Task<Location> = fusedLocationClient.lastLocation
            lastLocationTask.addOnSuccessListener { location: Location? -> // Explicitly nullable
                if (location != null) {
                    Log.d(TAG, "Last known location: ${location.latitude}, ${location.longitude}")
                    viewModel.updateLocation(location) // Call your ViewModel method
                } else {
                    Log.d(TAG, "No last known location available.")
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to get last known location.", e)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Lost location permission when trying to get last known location.", e)
        }
    }

    private fun hasLocationPermission(): Boolean {
        // This implementation is correct for checking fine or coarse location
        val fineLocationGranted = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocationGranted || coarseLocationGranted
    }
}
