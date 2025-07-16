package com.end3r.lootdropirl

import android.location.Location
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.end3r.lootdropirl.loot.LootGenerator
import com.end3r.lootdropirl.model.LootBox
import com.end3r.lootdropirl.model.UserInventory
import com.end3r.lootdropirl.repository.FirebaseRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainViewModel : ViewModel() {

    // Location data
    val currentLocation = MutableLiveData<Location>()
    val locationStatus = MutableLiveData<Boolean>()
    val shouldFollowUser = MutableLiveData<Boolean>()

    // Movement tracking
    val totalDistance = MutableLiveData<Float>()
    val distanceFromLastDrop = MutableLiveData<Float>()

    // Loot system
    val nearbyLootBoxes = MutableLiveData<List<LootBox>>()
    val userInventory = MutableLiveData<UserInventory>()
    val lootGenerationStatus = MutableLiveData<String>()
    val collectionStatus = MutableLiveData<String>()

    // Firebase and loot generation
    private val firebaseRepository = FirebaseRepository()
    private val lootGenerator = LootGenerator()
    private var lootBoxesListener: ListenerRegistration? = null

    private var lastLocation: Location? = null
    private var lastDropLocation: Location? = null
    private var cumulativeDistance: Float = 0f
    private var distanceFromDrop: Float = 0f

    companion object {
        private const val TAG = "MainViewModel"
    }

    init {
        // Initialize values
        totalDistance.value = 0f
        distanceFromLastDrop.value = 0f
        shouldFollowUser.value = true
        locationStatus.value = false
        lootGenerationStatus.value = "Ready to generate loot"
        collectionStatus.value = ""

        // Load user inventory
        loadUserInventory()
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

                // Update user stats in Firebase
                updateUserStats()
            }
        }

        // Update current location
        currentLocation.value = location
        lastLocation = location

        // If this is the first location, set as drop location
        if (lastDropLocation == null) {
            lastDropLocation = location
        }

        // Check if we should generate loot
        checkLootGeneration()

        // Update nearby loot boxes listener
        updateLootBoxesListener(location)
    }

    private fun checkLootGeneration() {
        if (lootGenerator.shouldGenerateLoot(distanceFromDrop)) {
            generateLootBox()
        }
    }

    private fun generateLootBox() {
        val location = currentLocation.value ?: return
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                lootGenerationStatus.value = "Generating loot box..."

                val lootBox = lootGenerator.generateLootBox(
                    location.latitude,
                    location.longitude,
                    userId
                )

                val result = firebaseRepository.saveLootBox(lootBox)

                if (result.isSuccess) {
                    lootGenerationStatus.value = "Loot box generated! Check your map!"
                    resetDistanceTracking()
                    Log.d(TAG, "Loot box generated successfully")
                } else {
                    lootGenerationStatus.value = "Failed to generate loot box"
                    Log.e(TAG, "Failed to generate loot box", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                lootGenerationStatus.value = "Error generating loot box"
                Log.e(TAG, "Error generating loot box", e)
            }
        }
    }

    private fun updateLootBoxesListener(location: Location) {
        // Remove previous listener
        lootBoxesListener?.remove()

        // Set up new listener for nearby loot boxes
        lootBoxesListener = firebaseRepository.listenToNearbyLootBoxes(
            location.latitude,
            location.longitude,
            onUpdate = { lootBoxes ->
                nearbyLootBoxes.value = lootBoxes
                Log.d(TAG, "Updated nearby loot boxes: ${lootBoxes.size}")
            },
            onError = { error ->
                Log.e(TAG, "Error listening to loot boxes", error)
            }
        )
    }

    fun collectLootBox(lootBoxId: String) {
        viewModelScope.launch {
            try {
                collectionStatus.value = "Collecting loot box..."

                val result = firebaseRepository.collectLootBox(lootBoxId)

                if (result.isSuccess) {
                    val lootBox = result.getOrNull()
                    collectionStatus.value = "Loot box collected! Got ${lootBox?.contents?.size ?: 0} items!"

                    // Reload inventory
                    loadUserInventory()

                    Log.d(TAG, "Loot box collected successfully: $lootBoxId")
                } else {
                    collectionStatus.value = "Failed to collect loot box"
                    Log.e(TAG, "Failed to collect loot box", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                collectionStatus.value = "Error collecting loot box"
                Log.e(TAG, "Error collecting loot box", e)
            }
        }
    }

    private fun loadUserInventory() {
        viewModelScope.launch {
            try {
                val result = firebaseRepository.getUserInventory()
                if (result.isSuccess) {
                    userInventory.value = result.getOrNull()
                    Log.d(TAG, "User inventory loaded successfully")
                } else {
                    Log.e(TAG, "Failed to load user inventory", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading user inventory", e)
            }
        }
    }

    private fun updateUserStats() {
        viewModelScope.launch {
            try {
                firebaseRepository.updateUserStats(cumulativeDistance)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating user stats", e)
            }
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

    fun getInventoryStats(): String {
        val inventory = userInventory.value
        return if (inventory != null) {
            "Items: ${inventory.items.size} | Boxes: ${inventory.totalLootBoxesCollected} | Distance: ${getFormattedDistance()}"
        } else {
            "Loading inventory..."
        }
    }

    override fun onCleared() {
        super.onCleared()
        lootBoxesListener?.remove()
    }
}
