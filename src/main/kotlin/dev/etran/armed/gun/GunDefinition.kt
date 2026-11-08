package dev.etran.armed.gun

import dev.etran.armed.util.Sounds
import org.bukkit.configuration.ConfigurationSection

/** Immutable snapshot of one gun from guns.yml. */
class GunDefinition(
    val id: String,
    val displayName: String,
    val material: String,
    val description: List<String>,
    val starter: Boolean,
    val permission: String,
    val hotbarSlot: Int,
    val damage: Double,
    val headshotMultiplier: Double,
    val fireRate: Double,
    val burstSize: Int,
    val clipSize: Int,
    val reloadTime: Double,
    val reserveAmmo: Int,
    val range: Double,
    val pellets: Int,
    val spread: Double,
    val knockback: Double,
    val fireTicks: Int,
    val costCurrency: String,
    val costAmount: Int,
    val shopEnabled: Boolean,
    val shopSlot: Int,
    val duel: String,
    val horde: String,
    val skill: String,
    val soundShoot: Sounds.Spec?,
    val soundReloadStart: Sounds.Spec?,
    val soundReloadEnd: Sounds.Spec?,
    val soundEmpty: Sounds.Spec?,
    val trailParticle: String,
    val trailStep: Double,
    val trailCount: Int,
    val trailSpread: Double
) {
    val reserveLimited: Boolean get() = reserveAmmo >= 0
    val free: Boolean get() = costAmount <= 0

    /** Milliseconds between trigger pulls: one fire-rate interval per shot in the burst. */
    val triggerCooldownMs: Long get() = (fireRate * 1000.0 * burstSize).toLong()

    /** Ticks between the shots of a burst. */
    val burstIntervalTicks: Long get() = Math.max(1L, Math.round(fireRate * 20.0))

    val reloadMs: Long get() = (reloadTime * 1000.0).toLong()

    companion object {
        fun fromSection(id: String, s: ConfigurationSection): GunDefinition = GunDefinition(
            id = id,
            displayName = s.getString("display-name") ?: "&f$id",
            material = s.getString("material") ?: "WOODEN_HOE",
            description = s.getStringList("description"),
            starter = s.getBoolean("starter", false),
            permission = s.getString("permission") ?: "",
            hotbarSlot = s.getInt("hotbar-slot", -1).coerceIn(-1, 8),
            damage = s.getDouble("stats.damage", 4.0).coerceAtLeast(0.0),
            headshotMultiplier = s.getDouble("stats.headshot-multiplier", 1.0).coerceAtLeast(0.0),
            fireRate = s.getDouble("stats.fire-rate", 0.4).coerceAtLeast(0.05),
            burstSize = s.getInt("stats.burst-size", 1).coerceIn(1, 20),
            clipSize = s.getInt("stats.clip-size", 12).coerceAtLeast(1),
            reloadTime = s.getDouble("stats.reload-time", 1.5).coerceAtLeast(0.0),
            reserveAmmo = s.getInt("stats.reserve-ammo", -1),
            range = s.getDouble("stats.range", 30.0).coerceIn(1.0, 200.0),
            pellets = s.getInt("stats.pellets", 1).coerceIn(1, 64),
            spread = s.getDouble("stats.spread", 0.0).coerceIn(0.0, 90.0),
            knockback = s.getDouble("stats.knockback", 0.0).coerceAtLeast(0.0),
            fireTicks = s.getInt("stats.fire-ticks", 0).coerceAtLeast(0),
            costCurrency = s.getString("cost.currency") ?: "iron",
            costAmount = s.getInt("cost.amount", 0).coerceAtLeast(0),
            shopEnabled = s.getBoolean("shop.enabled", true),
            shopSlot = s.getInt("shop.slot", -1),
            duel = s.getString("ratings.duel") ?: "Medium",
            horde = s.getString("ratings.horde") ?: "Medium",
            skill = s.getString("ratings.skill") ?: "Medium",
            soundShoot = Sounds.parse(s.getString("sounds.shoot")),
            soundReloadStart = Sounds.parse(s.getString("sounds.reload-start")),
            soundReloadEnd = Sounds.parse(s.getString("sounds.reload-end")),
            soundEmpty = Sounds.parse(s.getString("sounds.empty")),
            trailParticle = s.getString("trail.particle") ?: "none",
            trailStep = s.getDouble("trail.step", 2.0).coerceIn(0.2, 20.0),
            trailCount = s.getInt("trail.count", 1).coerceIn(1, 50),
            trailSpread = s.getDouble("trail.spread", 0.0).coerceIn(0.0, 2.0)
        )
    }
}
