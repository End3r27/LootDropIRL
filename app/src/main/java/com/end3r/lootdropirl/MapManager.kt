package com.end3r.lootdropirl

import android.graphics.Color
import android.util.Log
import com.end3r.lootdropirl.model.LootBox
import com.end3r.lootdropirl.model.LootType
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
    private val lootBoxMarkers = mutableMapOf<String, Marker>()

    companion object {
        private const val TAG = "MapManager"
        private const val DEFAULT_ZOOM = 16f
        private const val PATH_COLOR = Color.BLUE
        private const val PATH_WIDTH = 8f
        private const val LOOT_COLLECTION_RADIUS = 20f // meters
    }

    fun setupMap() {
        configureMap()
        observeLocationUpdates()
        observeLocationStatus()
        observeLootBoxUpdates()
        observeCollectedLootBoxes()
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
                    Log.d(TAG, "Camera move started: $reason")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception in configureMap: ${e.message}")
        }
    }

    private fun observeLocationUpdates() {
        viewModel.currentLocation.observe(viewModel.lifecycleOwner) { location ->
            location?.let {
                updateUserLocation(LatLng(it.latitude, it.longitude))
                checkNearbyLootBoxes(LatLng(it.latitude, it.longitude))
            }
        }
    }

    private fun observeLocationStatus() {
        viewModel.locationPermissionGranted.observe(viewModel.lifecycleOwner) { granted ->
            if (granted) {
                enableLocationFeatures()
            } else {
                disableLocationFeatures()
            }
        }
    }

    private fun observeLootBoxUpdates() {
        viewModel.nearbyLootBoxes.observe(viewModel.lifecycleOwner) { lootBoxes ->
            updateLootBoxMarkers(lootBoxes)
        }
    }

    private fun observeCollectedLootBoxes() {
        viewModel.collectedLootBoxes.observe(viewModel.lifecycleOwner) { collectedIds ->
            removeCollectedLootBoxMarkers(collectedIds)
        }
    }

    private fun updateUserLocation(location: LatLng) {
        userLocationMarker?.remove()

        userLocationMarker = googleMap.addMarker(
            MarkerOptions()
                .position(location)
                .title("Your Location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
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
            if (!lootBoxMarkers.containsKey(lootBox.id)) {
                addLootBoxMarker(lootBox)
            }
        }
    }

    private fun addLootBoxMarker(lootBox: LootBox) {
        val markerColor = when (lootBox.lootType) {
            LootType.COMMON -> BitmapDescriptorFactory.HUE_GREEN
            LootType.RARE -> BitmapDescriptorFactory.HUE_ORANGE
            LootType.EPIC -> BitmapDescriptorFactory.HUE_VIOLET
            LootType.LEGENDARY -> BitmapDescriptorFactory.HUE_YELLOW
        }

        val marker = googleMap.addMarker(
            MarkerOptions()
                .position(LatLng(lootBox.latitude, lootBox.longitude))
                .title("${lootBox.lootType.name} Loot Box")
                .snippet("Tap to collect!")
                .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
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
            val lootBoxLocation = LatLng(lootBox.latitude, lootBox.longitude)
            val distance = calculateDistance(userLocation, lootBoxLocation)

            if (distance <= LOOT_COLLECTION_RADIUS) {
                viewModel.collectLootBox(lootBox.id)
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
            viewModel.collectLootBox(lootBoxId)
            true
        } else {
            false
        }
    }

    private fun handleMapClick(latLng: LatLng) {
        Log.d(TAG, "Map clicked at: ${latLng.latitude}, ${latLng.longitude}")
        // Add any map click handling logic here
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

            googleMap.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds.build(), 100)
            )
        }
    }

    fun cleanup() {
        userLocationMarker?.remove()
        pathPolyline?.remove()
        lootBoxMarkers.values.forEach { it.remove() }
        lootBoxMarkers.clear()
        pathPoints.clear()
    }
}