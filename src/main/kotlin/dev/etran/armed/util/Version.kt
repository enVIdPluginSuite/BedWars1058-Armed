package dev.etran.armed.util

import org.bukkit.Bukkit

/**
 * Server version helpers. Minecraft switched from "1.x.y" to year based versions ("26.1"), so the
 * checks below compare (major, minor) pairs and treat any major above 1 as newer than every 1.x release.
 */
object Version {
    val major: Int
    val minor: Int

    init {
        val raw = Bukkit.getBukkitVersion().substringBefore('-')
        val parts = raw.split('.').map { it.toIntOrNull() ?: 0 }
        major = parts.getOrElse(0) { 1 }
        minor = parts.getOrElse(1) { 8 }
    }

    fun atLeast(maj: Int, min: Int): Boolean = major > maj || (major == maj && minor >= min)

    /** 1.8 only: sounds use the old names and there is no off hand. */
    val isLegacy: Boolean = !atLeast(1, 9)

    /** 1.13+ "flattening": material names changed and item data values are gone. */
    val isFlattened: Boolean = atLeast(1, 13)

    /** 1.14+ has PersistentDataContainer on item meta. */
    val hasPdc: Boolean = atLeast(1, 14)

    val supportsHex: Boolean = atLeast(1, 16)

    override fun toString(): String = "$major.$minor"
}
