package com.end3r.lootdropirl.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.end3r.lootdropirl.model.*
import kotlinx.coroutines.tasks.await
import java.util.*

class FirebaseRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "FirebaseRepository"
        private const val COLLECTION_LOOT_BOXES = "loot_boxes"
        private const val COLLECTION_USER_INVENTORY = "user_inventory"
        private const val COLLECTION_USERS = "users"
        private const val LOOT_BOX_QUERY_RADIUS = 1000.0 // 1km radius for querying
    }

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: "anonymous"

    // Loot Box operations
    suspend fun saveLootBox(lootBox: LootBox): Result<String> {
        return try {
            val documentRef = firestore.collection(COLLECTION_LOOT_BOXES).document()
            val lootBoxWithId = lootBox.copy(id = documentRef.id)
            documentRef.set(lootBoxWithId).await()
            Log.d(TAG, "Loot box saved successfully: ${documentRef.id}")
            Result.success(documentRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving loot box", e)
            Result.failure(e)
        }
    }

    suspend fun getNearbyLootBoxes(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = LOOT_BOX_QUERY_RADIUS
    ): Result<List<LootBox>> {
        return try {
            // Note: For production, you'd want to use proper geospatial queries
            // This is a simplified version that gets all uncollected loot boxes
            val snapshot = firestore.collection(COLLECTION_LOOT_BOXES)
                .whereEqualTo("isCollected", false)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .await()

            val lootBoxes = snapshot.toObjects(LootBox::class.java)

            // Filter by distance (simplified approach)
            val filteredLootBoxes = lootBoxes.filter { lootBox ->
                val distance = calculateDistance(latitude, longitude, lootBox.latitude, lootBox.longitude)
                distance <= radiusKm * 1000 // Convert km to meters
            }

            Log.d(TAG, "Retrieved ${filteredLootBoxes.size} nearby loot boxes")
            Result.success(filteredLootBoxes)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting nearby loot boxes", e)
            Result.failure(e)
        }
    }

    suspend fun collectLootBox(lootBoxId: String): Result<LootBox> {
        return try {
            val lootBoxRef = firestore.collection(COLLECTION_LOOT_BOXES).document(lootBoxId)
            val lootBoxSnapshot = lootBoxRef.get().await()

            if (!lootBoxSnapshot.exists()) {
                return Result.failure(Exception("Loot box not found"))
            }

            val lootBox = lootBoxSnapshot.toObject(LootBox::class.java)
                ?: return Result.failure(Exception("Invalid loot box data"))

            if (lootBox.isCollected) {
                return Result.failure(Exception("Loot box already collected"))
            }

            // Mark as collected
            val updatedLootBox = lootBox.copy(
                isCollected = true,
                collectedBy = currentUserId,
                collectedAt = Date()
            )

            lootBoxRef.set(updatedLootBox).await()

            // Add items to user inventory
            addItemsToInventory(lootBox.contents)

            Log.d(TAG, "Loot box collected successfully: $lootBoxId")
            Result.success(updatedLootBox)
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting loot box", e)
            Result.failure(e)
        }
    }

    // User Inventory operations
    suspend fun getUserInventory(): Result<UserInventory> {
        return try {
            val inventoryRef = firestore.collection(COLLECTION_USER_INVENTORY).document(currentUserId)
            val snapshot = inventoryRef.get().await()

            val inventory = if (snapshot.exists()) {
                snapshot.toObject(UserInventory::class.java) ?: UserInventory(userId = currentUserId)
            } else {
                UserInventory(userId = currentUserId)
            }

            Log.d(TAG, "Retrieved user inventory with ${inventory.items.size} items")
            Result.success(inventory)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user inventory", e)
            Result.failure(e)
        }
    }

    suspend fun addItemsToInventory(items: List<LootItem>): Result<Unit> {
        return try {
            val inventoryRef = firestore.collection(COLLECTION_USER_INVENTORY).document(currentUserId)
            val currentInventory = getUserInventory().getOrNull() ?: UserInventory(userId = currentUserId)

            // Convert items to inventory items
            val newInventoryItems = items.map { lootItem ->
                InventoryItem(
                    lootItem = lootItem,
                    quantity = 1,
                    acquiredAt = Date()
                )
            }

            // Merge with existing items (combine quantities for same items)
            val mergedItems = mutableListOf<InventoryItem>()
            val existingItems = currentInventory.items.toMutableList()

            for (newItem in newInventoryItems) {
                val existingItem = existingItems.find { it.lootItem.id == newItem.lootItem.id }
                if (existingItem != null) {
                    // Update quantity
                    val updatedItem = existingItem.copy(quantity = existingItem.quantity + newItem.quantity)
                    existingItems.remove(existingItem)
                    existingItems.add(updatedItem)
                } else {
                    existingItems.add(newItem)
                }
            }

            val updatedInventory = currentInventory.copy(
                items = existingItems,
                totalLootBoxesCollected = currentInventory.totalLootBoxesCollected + 1,
                lastUpdated = Date()
            )

            inventoryRef.set(updatedInventory).await()

            Log.d(TAG, "Added ${items.size} items to inventory")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding items to inventory", e)
            Result.failure(e)
        }
    }

    suspend fun updateUserStats(totalDistance: Float): Result<Unit> {
        return try {
            val inventoryRef = firestore.collection(COLLECTION_USER_INVENTORY).document(currentUserId)
            val currentInventory = getUserInventory().getOrNull() ?: UserInventory(userId = currentUserId)

            val updatedInventory = currentInventory.copy(
                totalDistanceTraveled = totalDistance,
                lastUpdated = Date()
            )

            inventoryRef.set(updatedInventory).await()

            Log.d(TAG, "Updated user stats - distance: $totalDistance")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user stats", e)
            Result.failure(e)
        }
    }

    // Real-time listeners
    fun listenToNearbyLootBoxes(
        latitude: Double,
        longitude: Double,
        onUpdate: (List<LootBox>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return firestore.collection(COLLECTION_LOOT_BOXES)
            .whereEqualTo("isCollected", false)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to loot boxes", error)
                    onError(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val lootBoxes = snapshot.toObjects(LootBox::class.java)

                    // Filter by distance
                    val filteredLootBoxes = lootBoxes.filter { lootBox ->
                        val distance = calculateDistance(latitude, longitude, lootBox.latitude, lootBox.longitude)
                        distance <= LOOT_BOX_QUERY_RADIUS
                    }

                    onUpdate(filteredLootBoxes)
                }
            }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // Earth's radius in meters
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLat = Math.toRadians(lat2 - lat1)
        val deltaLon = Math.toRadians(lon2 - lon1)

        val a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }
}