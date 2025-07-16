package com.end3r.lootdropirl

import android.graphics.Color
import android.util.Log
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*

class MapManager(
    private val googleMap: GoogleMap,
    private val viewModel: MainViewModel
) {

    private var userLocationMarker: Marker? = null
    private var pathPolyline: Polyline? = null
    private val pathPoints = mutableListOf<LatLng>()

    companion object {
        private const val TAG = "MapManager"
        private const val DEFAULT_ZOOM = 16f
        private const val PATH_COLOR = Color.BLUE
        private const val PATH_WIDTH = 8f
    }

    fun setupMap() {
        configureMap()
        observeLocationUpdates()
        observeLocationStatus()
    }

    private fun configureMap() {
        googleMap.apply {
            mapType = GoogleMap.MAP_TYPE_NORMAL

            // Enable UI controls
            uiSettings.isZoomControlsEnabled = true
            uiSettings.isCompassEnabled = true
            uiSettings.isMyLocationButtonEnabled = false // We'll handle this manually
            uiSettings.isMapToolbarEnabled = false

            // Set map style (optional - dark theme)
            // setMapStyle(MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style))

            Log.d(TAG, "Map configured successfully")
        }
    }

    private fun observeLocationUpdates() {
        viewModel.currentLocation.observeForever { location ->
            location?.let {
                updateUserLocation(it)
                updatePath(it)
            }
        }
    }

    private fun observeLocationStatus() {
        viewModel.locationStatus.observeForever { isAvailable ->
            if (isAvailable) {
                Log.d(TAG, "Location services available")
            } else {
                Log.w(TAG, "Location services not available")
            }
        }
    }

    private fun updateUserLocation(location: android.location.Location) {
        val latLng = LatLng(location.latitude, location.longitude)

        // Update or create user location marker
        if (userLocationMarker == null) {
            userLocationMarker = googleMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("You are here")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )

            // Center camera on user location for first time
            googleMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM)
            )
        } else {
            // Update existing marker position
            userLocationMarker?.position = latLng

            // Optionally keep camera following user (can be toggled)
            if (viewModel.shouldFollowUser.value == true) {
                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLng(latLng)
                )
            }
        }

        Log.d(TAG, "User location updated: $latLng")
    }

    private fun updatePath(location: android.location.Location) {
        val latLng = LatLng(location.latitude, location.longitude)

        // Add point to path
        pathPoints.add(latLng)

        // Update polyline
        pathPolyline?.remove()
        pathPolyline = googleMap.addPolyline(
            PolylineOptions()
                .addAll(pathPoints)
                .color(PATH_COLOR)
                .width(PATH_WIDTH)
                .geodesic(true)
        )

        Log.d(TAG, "Path updated with ${pathPoints.size} points")
    }

    fun clearPath() {
        pathPoints.clear()
        pathPolyline?.remove()
        pathPolyline = null
        Log.d(TAG, "Path cleared")
    }

    fun toggleFollowUser() {
        val currentSetting = viewModel.shouldFollowUser.value ?: false
        viewModel.shouldFollowUser.value = !currentSetting

        if (!currentSetting) {
            // If we're now following, center on user
            viewModel.currentLocation.value?.let { location ->
                val latLng = LatLng(location.latitude, location.longitude)
                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLng(latLng)
                )
            }
        }
    }
}