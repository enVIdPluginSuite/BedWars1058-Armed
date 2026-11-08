package dev.etran.armed.util

import org.bukkit.Location
import org.bukkit.entity.Player

/**
 * Sounds are plain strings ("entity.arrow.shoot 1.0 1.5") rather than org.bukkit.Sound, because Sound
 * stopped being an enum in recent versions and its constants were renamed repeatedly. The string overload
 * of playSound exists in every version.
 */
object Sounds {
    class Spec(val key: String, val volume: Float, val pitch: Float)

    private val legacy = mapOf(
        "entity.arrow.shoot" to "random.bow",
        "entity.arrow.hit_player" to "random.successful_hit",
        "entity.generic.explode" to "random.explode",
        "item.firecharge.use" to "item.fireCharge.use",
        "block.piston.contract" to "tile.piston.in",
        "block.piston.extend" to "tile.piston.out",
        "block.dispenser.fail" to "random.click",
        "ui.button.click" to "random.click",
        "entity.experience_orb.pickup" to "random.orb",
        "entity.villager.no" to "mob.villager.no",
        "entity.player.levelup" to "random.levelup",
        "item.flintandsteel.use" to "fire.ignite",
        "block.fire.ambient" to "fire.fire",
        "entity.item.pickup" to "random.pop",
        "block.anvil.land" to "random.anvil_land",
        "entity.firework.blast" to "fireworks.blast",
        "entity.blaze.shoot" to "mob.ghast.fireball"
    )

    fun parse(raw: String?): Spec? {
        if (raw == null) return null
        val parts = raw.trim().split(Regex("\\s+"))
        val key = parts.getOrNull(0)?.lowercase() ?: return null
        if (key.isEmpty() || key == "none") return null
        val vol = parts.getOrNull(1)?.toFloatOrNull() ?: 1.0f
        val pitch = (parts.getOrNull(2)?.toFloatOrNull() ?: 1.0f).coerceIn(0.5f, 2.0f)
        return Spec(key, vol, pitch)
    }

    private fun resolveKey(key: String): String {
        if (Version.isLegacy) return legacy[key.removePrefix("minecraft:")] ?: key
        return key
    }

    fun play(player: Player, spec: Spec?, at: Location = player.location) {
        if (spec == null) return
        try {
            player.playSound(at, resolveKey(spec.key), spec.volume, spec.pitch)
        } catch (_: Throwable) {
        }
    }

    fun play(player: Player, raw: String?) = play(player, parse(raw))

    /** Plays [spec] for everybody within [radius] blocks of [at]. */
    fun playAround(at: Location, spec: Spec?, radius: Double) {
        if (spec == null) return
        val world = at.world ?: return
        val r2 = radius * radius
        for (p in world.players) {
            if (p.location.distanceSquared(at) <= r2) play(p, spec, at)
        }
    }
}
