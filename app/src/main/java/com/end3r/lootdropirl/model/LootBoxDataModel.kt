package com.end3r.lootdropirl.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.*

data class LootBox(
    @DocumentId
    val id: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val lootType: LootType = LootType.COMMON,
    val contents: List<LootItem> = emptyList(),
    val isCollected: Boolean = false,
    val createdBy: String = "",
    @ServerTimestamp
    val createdAt: Date? = null,
    val collectedBy: String? = null,
    @ServerTimestamp
    val collectedAt: Date? = null
)

data class LootItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: ItemType = ItemType.CONSUMABLE,
    val rarity: Rarity = Rarity.COMMON,
    val value: Int = 0,
    val description: String = "",
    val iconResource: String = ""
)

data class UserInventory(
    val userId: String = "",
    val items: List<InventoryItem> = emptyList(),
    val totalLootBoxesCollected: Int = 0,
    val totalDistanceTraveled: Float = 0f,
    @ServerTimestamp
    val lastUpdated: Date? = null
)

data class InventoryItem(
    val lootItem: LootItem,
    val quantity: Int = 1,
    val acquiredAt: Date = Date()
)

enum class LootType(val displayName: String, val color: String) {
    COMMON("Common", "#95a5a6"),
    UNCOMMON("Uncommon", "#2ecc71"),
    RARE("Rare", "#3498db"),
    EPIC("Epic", "#9b59b6"),
    LEGENDARY("Legendary", "#f39c12")
}

enum class ItemType(val displayName: String) {
    CONSUMABLE("Consumable"),
    EQUIPMENT("Equipment"),
    CURRENCY("Currency"),
    COLLECTIBLE("Collectible")
}

enum class Rarity(val displayName: String, val color: String) {
    COMMON("Common", "#95a5a6"),
    UNCOMMON("Uncommon", "#2ecc71"),
    RARE("Rare", "#3498db"),
    EPIC("Epic", "#9b59b6"),
    LEGENDARY("Legendary", "#f39c12")
}