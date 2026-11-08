package dev.etran.armed.util

import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * Material lookup that understands both pre-1.13 and 1.13+ names, so one config works on every version.
 * Everything goes through strings: referencing Material constants directly would break when a name
 * does not exist on the running server.
 */
object Mats {
    private val pairs = listOf(
        "CLOCK" to "WATCH",
        "FIRE_CHARGE" to "FIREBALL",
        "GUNPOWDER" to "SULPHUR",
        "SNOWBALL" to "SNOW_BALL",
        "FIREWORK_ROCKET" to "FIREWORK",
        "PLAYER_HEAD" to "SKULL_ITEM",
        "COMPARATOR" to "REDSTONE_COMPARATOR",
        "ENDER_EYE" to "EYE_OF_ENDER",
        "GRAY_STAINED_GLASS_PANE" to "STAINED_GLASS_PANE",
        "BLACK_STAINED_GLASS_PANE" to "STAINED_GLASS_PANE",
        "OAK_PLANKS" to "WOOD",
        "CRAFTING_TABLE" to "WORKBENCH"
    )

    fun resolve(name: String?): Material? {
        if (name == null) return null
        val n = name.trim().uppercase().replace(' ', '_').removePrefix("MINECRAFT:")
        if (n.isEmpty()) return null
        val candidates = LinkedHashSet<String>()
        candidates += n
        candidates += n.replace("WOODEN_", "WOOD_")
        candidates += n.replace("WOOD_", "WOODEN_")
        candidates += n.replace("GOLDEN_", "GOLD_")
        candidates += n.replace("GOLD_", "GOLDEN_")
        for ((modern, legacy) in pairs) {
            if (n == modern) candidates += legacy
            if (n == legacy) candidates += modern
        }
        for (c in candidates) {
            val m = Material.matchMaterial(c) ?: continue
            if (m.name.startsWith("LEGACY_")) continue
            return m
        }
        return null
    }

    fun resolve(vararg names: String): Material? {
        for (n in names) resolve(n)?.let { return it }
        return null
    }

    /** Builds an ItemStack from "MATERIAL" or "MATERIAL:data" (data is only honoured before 1.13). */
    @Suppress("DEPRECATION")
    fun stack(spec: String?, amount: Int = 1, fallback: String = "STICK"): ItemStack {
        var matName = spec ?: fallback
        var data = 0
        if (matName.contains(':') && !matName.lowercase().startsWith("minecraft:")) {
            data = matName.substringAfter(':').toIntOrNull() ?: 0
            matName = matName.substringBefore(':')
        }
        val upper = matName.trim().uppercase()
        val m = resolve(matName) ?: resolve(fallback) ?: resolve("STICK")!!
        // Stained glass panes exist as a single material with colour in the data value before 1.13.
        if (!Version.isFlattened && m.name == "STAINED_GLASS_PANE" && data == 0) {
            data = when {
                upper.startsWith("GRAY") -> 7
                upper.startsWith("BLACK") -> 15
                upper.startsWith("WHITE") -> 0
                else -> 0
            }
        }
        return if (!Version.isFlattened && data != 0) ItemStack(m, amount, data.toShort()) else ItemStack(m, amount)
    }
}
