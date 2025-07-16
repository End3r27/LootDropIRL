package com.end3r.lootdropirl

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.maps.SupportMapFragment

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var mapFragment: SupportMapFragment

    // Permission launcher
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Precise location access granted
                initializeMap()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Approximate location access granted
                initializeMap()
            }
            else -> {
                // No location access granted
                showPermissionDeniedMessage()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Setup map fragment
        mapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment

        // Check permissions and initialize
        checkLocationPermissions()
    }

    private fun checkLocationPermissions() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
                initializeMap()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                // Show rationale and request permission
                showLocationPermissionRationale()
            }
            else -> {
                // Request permission directly
                requestLocationPermissions()
            }
        }
    }

    private fun requestLocationPermissions() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun showLocationPermissionRationale() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Location Permission Required")
            .setMessage("LootDrop IRL needs location access to track your movement and spawn loot boxes around you.")
            .setPositiveButton("Grant Permission") { _, _ ->
                requestLocationPermissions()
            }
            .setNegativeButton("Cancel") { _, _ ->
                showPermissionDeniedMessage()
            }
            .show()
    }

    private fun showPermissionDeniedMessage() {
        Toast.makeText(
            this,
            "Location permission is required for LootDrop IRL to work properly",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun initializeMap() {
        mapFragment.getMapAsync { googleMap ->
            val mapManager = MapManager(googleMap, viewModel)
            mapManager.setupMap()

            // Start location tracking
            val locationTracker = LocationTracker(this, viewModel)
            locationTracker.startLocationUpdates()
        }
    }
}