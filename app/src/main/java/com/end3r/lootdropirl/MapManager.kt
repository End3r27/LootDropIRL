package com.end3r.lootdropirl

import android.graphics.Color
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import com.end3r.lootdropirl.model.LootBox
import com.end3r.lootdropirl.model.LootType
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*

class MapManager(
    private val googleMap: GoogleMap,
    private val viewModel: MainViewModel,
    private val lifecycleOwner: LifecycleOwner
) {

    private var userLocationMarker: Marker? = null
    private var pathPolyline: Polyline? = null
    private val pathPoints = mutableListOf<LatLng>()
    private val lootBoxMarkers = mutableMapOf<String, Marker>()
    private var collectionRadiusCircle: Circle? = null

    companion object {
        private const val TAG = "MapManager"
        private const val DEFAULT_ZOOM = 16f
        private const val PATH_COLOR = Color.BLUE
        private const val PATH_WIDTH = 8f
        private const val LOOT_COLLECTION_RADIUS = 20f // meters
        private val COLLECTION_RADIUS_COLOR = Color.argb(50, 0, 255, 0)
        private val COLLECTION_RADIUS_STROKE_COLOR = Color.argb(100, 0, 255, 0)
    }

    fun setupMap() {
        configureMap()
        observeLocationUpdates()
        observeLocationStatus()
        observeLootBoxUpdates()
        observeCollectionStatus()
    }

    private fun configureMap() {
        try {
            googleMap.apply {
                mapType = GoogleMap.MAP_TYPE_NORMAL
                uiSettings.isZoomControlsEnabled = true
                uiSettings.isCompassEnabled = true
                uiSettings.isMyLocationButtonEnabled = false
                isMyLocationEnabled = false

                setOnMarkerClickListener { marker ->
                    handleMarkerClick(marker)
                }

                setOnMapClickListener { latLng ->
                    handleMapClick(latLng)
                }

                setOnCameraMoveStartedListener { reason ->
                    if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                        viewModel.shouldFollowUser.value = false
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception in configureMap: ${e.message}")
        }
    }

    private fun observeLocationUpdates() {
        viewModel.currentLocation.observe(lifecycleOwner) { location ->
            location?.let {
                val latLng = LatLng(it.latitude, it.longitude)
                updateUserLocation(latLng)
                updateCollectionRadius(latLng)
                checkNearbyLootBoxes(latLng)

                // Auto-follow user if enabled
                if (viewModel.shouldFollowUser.value == true) {
                    centerOnUserLocation()
                }
            }
        }
    }

    private fun observeLocationStatus() {
        viewModel.locationStatus.observe(lifecycleOwner) { isAvailable ->
            if (isAvailable) {
                enableLocationFeatures()
            } else {
                disableLocationFeatures()
            }
        }
    }

    private fun observeLootBoxUpdates() {
        viewModel.nearbyLootBoxes.observe(lifecycleOwner) { lootBoxes ->
            updateLootBoxMarkers(lootBoxes)
        }
    }

    private fun observeCollectionStatus() {
        viewModel.collectionStatus.observe(lifecycleOwner) { status ->
            if (status.contains("collected")) {
                // Show brief collection animation or notification
                Log.d(TAG, "Collection status: $status")
            }
        }
    }

    private fun updateUserLocation(location: LatLng) {
        userLocationMarker?.remove()

        userLocationMarker = googleMap.addMarker(
            MarkerOptions()
                .position(location)
                .title("Your Location")
                .snippet("Distance: ${viewModel.getFormattedDistance()}")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                .anchor(0.5f, 0.5f)
        )

        // Add to path
        pathPoints.add(location)
        updatePathPolyline()

        // Move camera to user location if first time
        if (pathPoints.size == 1) {
            googleMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(location, DEFAULT_ZOOM)
            )
        }
    }

    private fun updateCollectionRadius(location: LatLng) {
        collectionRadiusCircle?.remove()

        collectionRadiusCircle = googleMap.addCircle(
            CircleOptions()
                .center(location)
                .radius(LOOT_COLLECTION_RADIUS.toDouble())
                .fillColor(COLLECTION_RADIUS_COLOR)
                .strokeColor(COLLECTION_RADIUS_STROKE_COLOR)
                .strokeWidth(2f)
        )
    }

    private fun updatePathPolyline() {
        pathPolyline?.remove()

        if (pathPoints.size >= 2) {
            pathPolyline = googleMap.addPolyline(
                PolylineOptions()
                    .addAll(pathPoints)
                    .color(PATH_COLOR)
                    .width(PATH_WIDTH)
                    .geodesic(true)
            )
        }
    }

    private fun updateLootBoxMarkers(lootBoxes: List<LootBox>) {
        // Remove markers for loot boxes that are no longer nearby
        val currentLootBoxIds = lootBoxes.map { it.id }.toSet()
        lootBoxMarkers.keys.filter { !currentLootBoxIds.contains(it) }.forEach { id ->
            lootBoxMarkers[id]?.remove()
            lootBoxMarkers.remove(id)
        }

        // Add or update markers for current loot boxes
        lootBoxes.forEach { lootBox ->
            if (!lootBoxMarkers.containsKey(lootBox.id) && !lootBox.isCollected) {
                addLootBoxMarker(lootBox)
            }
        }
    }

    private fun addLootBoxMarker(lootBox: LootBox) {
        val markerColor = when (lootBox.lootType) {
            LootType.COMMON -> BitmapDescriptorFactory.HUE_GREEN
            LootType.UNCOMMON -> BitmapDescriptorFactory.HUE_CYAN
            LootType.RARE -> BitmapDescriptorFactory.HUE_ORANGE
            LootType.EPIC -> BitmapDescriptorFactory.HUE_VIOLET
            LootType.LEGENDARY -> BitmapDescriptorFactory.HUE_YELLOW
        }

        val distanceToLoot = viewModel.currentLocation.value?.let { userLocation ->
            LocationUtils.calculateDistance(
                userLocation.latitude, userLocation.longitude,
                lootBox.latitude, lootBox.longitude
            )
        } ?: 0f

        val marker = googleMap.addMarker(
            MarkerOptions()
                .position(LatLng(lootBox.latitude, lootBox.longitude))
                .title("${lootBox.lootType.displayName} Loot Box")
                .snippet("${lootBox.contents.size} items • ${LocationUtils.formatDistance(distanceToLoot)} away")
                .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
                .anchor(0.5f, 1.0f)
        )

        marker?.tag = lootBox.id
        marker?.let { lootBoxMarkers[lootBox.id] = it }
    }

    private fun removeCollectedLootBoxMarkers(collectedIds: Set<String>) {
        collectedIds.forEach { id ->
            lootBoxMarkers[id]?.remove()
            lootBoxMarkers.remove(id)
        }
    }

    private fun checkNearbyLootBoxes(userLocation: LatLng) {
        val currentLootBoxes = viewModel.nearbyLootBoxes.value ?: return

        currentLootBoxes.forEach { lootBox ->
            if (!lootBox.isCollected) {
                val lootBoxLocation = LatLng(lootBox.latitude, lootBox.longitude)
                val distance = calculateDistance(userLocation, lootBoxLocation)

                if (distance <= LOOT_COLLECTION_RADIUS) {
                    viewModel.collectLootBox(lootBox.id)
                }
            }
        }
    }

    private fun calculateDistance(point1: LatLng, point2: LatLng): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            point1.latitude, point1.longitude,
            point2.latitude, point2.longitude,
            results
        )
        return results[0]
    }

    private fun handleMarkerClick(marker: Marker): Boolean {
        val lootBoxId = marker.tag as? String
        return if (lootBoxId != null) {
            // Check if loot box is within collection range
            val userLocation = viewModel.currentLocation.value
            if (userLocation != null) {
                val distance = calculateDistance(
                    LatLng(userLocation.latitude, userLocation.longitude),
                    marker.position
                )

                if (distance <= LOOT_COLLECTION_RADIUS) {
                    viewModel.collectLootBox(lootBoxId)
                } else {
                    // Show info window with distance
                    marker.showInfoWindow()
                }
            }
            true
        } else {
            false
        }
    }

    private fun handleMapClick(latLng: LatLng) {
        Log.d(TAG, "Map clicked at: ${latLng.latitude}, ${latLng.longitude}")
        // Re-enable user following when map is clicked
        viewModel.shouldFollowUser.value = true
    }

    private fun enableLocationFeatures() {
        try {
            googleMap.isMyLocationEnabled = true
            googleMap.uiSettings.isMyLocationButtonEnabled = true
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception enabling location features: ${e.message}")
        }
    }

    private fun disableLocationFeatures() {
        try {
            googleMap.isMyLocationEnabled = false
            googleMap.uiSettings.isMyLocationButtonEnabled = false
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception disabling location features: ${e.message}")
        }
    }

    fun centerOnUserLocation() {
        userLocationMarker?.position?.let { position ->
            googleMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(position, DEFAULT_ZOOM)
            )
        }
    }

    fun toggleUserFollowing() {
        val currentFollowing = viewModel.shouldFollowUser.value ?: true
        viewModel.shouldFollowUser.value = !currentFollowing

        if (!currentFollowing) {
            centerOnUserLocation()
        }
    }

    fun clearPath() {
        pathPolyline?.remove()
        pathPoints.clear()
    }

    fun showAllLootBoxes() {
        val allMarkers = mutableListOf<LatLng>()

        userLocationMarker?.position?.let { allMarkers.add(it) }
        lootBoxMarkers.values.forEach { marker ->
            allMarkers.add(marker.position)
        }

        if (allMarkers.isNotEmpty()) {
            val bounds = LatLngBounds.Builder()
            allMarkers.forEach { bounds.include(it) }

            try {
                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds.build(), 100)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error showing all loot boxes", e)
            }
        }
    }

    fun cleanup() {
        userLocationMarker?.remove()
        pathPolyline?.remove()
        collectionRadiusCircle?.remove()
        lootBoxMarkers.values.forEach { it.remove() }
        lootBoxMarkers.clear()
        pathPoints.clear()
    }
}