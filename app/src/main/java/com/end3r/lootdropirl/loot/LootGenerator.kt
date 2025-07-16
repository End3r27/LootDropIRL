package com.end3r.lootdropirl.loot

import com.end3r.lootdropirl.model.*
import kotlin.random.Random

class LootGenerator {

    companion object {
        private const val LOOT_DROP_DISTANCE_THRESHOLD = 100f // 100 meters
        private const val LOOT_BOX_SPAWN_RADIUS = 50f // 50 meters around player

        // Loot type probabilities (out of 100)
        private val LOOT_TYPE_PROBABILITIES = mapOf(
            LootType.COMMON to 50,
            LootType.UNCOMMON to 30,
            LootType.RARE to 15,
            LootType.EPIC to 4,
            LootType.LEGENDARY to 1
        )

        // Predefined loot items
        private val LOOT_ITEMS = listOf(
            // Common items
            LootItem("health_potion_small", "Small Health Potion", ItemType.CONSUMABLE, Rarity.COMMON, 10, "Restores 50 HP"),
            LootItem("coins_small", "Gold Coins", ItemType.CURRENCY, Rarity.COMMON, 25, "25 gold coins"),
            LootItem("bread", "Bread", ItemType.CONSUMABLE, Rarity.COMMON, 5, "Basic food item"),

            // Uncommon items
            LootItem("health_potion_medium", "Health Potion", ItemType.CONSUMABLE, Rarity.UNCOMMON, 30, "Restores 100 HP"),
            LootItem("coins_medium", "Gold Coins", ItemType.CURRENCY, Rarity.UNCOMMON, 50, "50 gold coins"),
            LootItem("iron_sword", "Iron Sword", ItemType.EQUIPMENT, Rarity.UNCOMMON, 75, "A sturdy iron sword"),

            // Rare items
            LootItem("health_potion_large", "Greater Health Potion", ItemType.CONSUMABLE, Rarity.RARE, 60, "Restores 200 HP"),
            LootItem("coins_large", "Gold Coins", ItemType.CURRENCY, Rarity.RARE, 100, "100 gold coins"),
            LootItem("steel_armor", "Steel Armor", ItemType.EQUIPMENT, Rarity.RARE, 150, "Protective steel armor"),

            // Epic items
            LootItem("elixir", "Elixir of Power", ItemType.CONSUMABLE, Rarity.EPIC, 200, "Grants temporary power boost"),
            LootItem("enchanted_bow", "Enchanted Bow", ItemType.EQUIPMENT, Rarity.EPIC, 300, "A magical bow with increased damage"),
            LootItem("gems", "Precious Gems", ItemType.COLLECTIBLE, Rarity.EPIC, 250, "Rare gemstones"),

            // Legendary items
            LootItem("phoenix_feather", "Phoenix Feather", ItemType.COLLECTIBLE, Rarity.LEGENDARY, 1000, "Legendary phoenix feather"),
            LootItem("dragons_blade", "Dragon's Blade", ItemType.EQUIPMENT, Rarity.LEGENDARY, 500, "A legendary sword forged by dragons"),
            LootItem("treasure_chest", "Ancient Treasure", ItemType.COLLECTIBLE, Rarity.LEGENDARY, 750, "Ancient treasure of immense value")
        )
    }

    fun shouldGenerateLoot(distanceFromLastDrop: Float): Boolean {
        return distanceFromLastDrop >= LOOT_DROP_DISTANCE_THRESHOLD
    }

    fun generateLootBox(playerLat: Double, playerLng: Double, userId: String): LootBox {
        val lootType = generateLootType()
        val contents = generateLootContents(lootType)
        val (lat, lng) = generateRandomLocation(playerLat, playerLng)

        return LootBox(
            latitude = lat,
            longitude = lng,
            lootType = lootType,
            contents = contents,
            createdBy = userId
        )
    }

    private fun generateLootType(): LootType {
        val randomValue = Random.nextInt(1, 101) // 1-100
        var cumulativeProbability = 0

        for ((lootType, probability) in LOOT_TYPE_PROBABILITIES) {
            cumulativeProbability += probability
            if (randomValue <= cumulativeProbability) {
                return lootType
            }
        }

        return LootType.COMMON // Fallback
    }

    private fun generateLootContents(lootType: LootType): List<LootItem> {
        val availableItems = LOOT_ITEMS.filter { it.rarity.ordinal <= lootType.ordinal }
        val itemCount = when (lootType) {
            LootType.COMMON -> Random.nextInt(1, 3) // 1-2 items
            LootType.UNCOMMON -> Random.nextInt(1, 4) // 1-3 items
            LootType.RARE -> Random.nextInt(2, 5) // 2-4 items
            LootType.EPIC -> Random.nextInt(2, 6) // 2-5 items
            LootType.LEGENDARY -> Random.nextInt(3, 7) // 3-6 items
        }

        return (1..itemCount).map {
            availableItems.random()
        }
    }

    private fun generateRandomLocation(playerLat: Double, playerLng: Double): Pair<Double, Double> {
        // Generate random point within spawn radius
        val angle = Random.nextDouble() * 2 * Math.PI
        val radius = Random.nextDouble(10.0, LOOT_BOX_SPAWN_RADIUS.toDouble())

        // Convert to lat/lng offset (approximate)
        val latOffset = (radius * Math.cos(angle)) / 111320.0 // ~111.32 km per degree
        val lngOffset = (radius * Math.sin(angle)) / (111320.0 * Math.cos(Math.toRadians(playerLat)))

        return Pair(playerLat + latOffset, playerLng + lngOffset)
    }

    fun getLootBoxDescription(lootBox: LootBox): String {
        return "${lootBox.lootType.displayName} Loot Box (${lootBox.contents.size} items)"
    }
}