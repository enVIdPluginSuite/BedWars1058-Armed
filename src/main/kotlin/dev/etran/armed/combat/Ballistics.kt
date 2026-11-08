package dev.etran.armed.combat

import dev.etran.armed.ArmedPlugin
import dev.etran.armed.bedwars.BedWarsHook
import dev.etran.armed.gun.GunDefinition
import dev.etran.armed.util.Particles
import dev.etran.armed.util.Sounds
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import java.util.Random

/**
 * Hitscan bullets. A shot is a ray from the eyes: it stops at the first solid block, and hits the nearest
 * living target whose hitbox the ray crosses before that. This is version independent (no projectile
 * entities, no World#rayTrace which only exists on 1.13+).
 */
class Ballistics(private val plugin: dev.etran.armed.ArmedPlugin) {
    private val random = Random()

    private class Hit(var damage: Double = 0.0, var headshot: Boolean = false)

    fun fire(shooter: Player, gun: dev.etran.armed.gun.GunDefinition) {
        val s = plugin.settings
        val origin = shooter.eyeLocation
        val baseDir = origin.direction.normalize()
        _root_ide_package_.dev.etran.armed.util.Sounds.playAround(shooter.location, gun.soundShoot, s.soundRadius)

        val candidates = candidates(shooter, gun)
        val hits = LinkedHashMap<LivingEntity, Hit>()

        for (pellet in 0 until gun.pellets) {
            val dir = if (gun.spread > 0.0) spread(baseDir, gun.spread) else baseDir
            val blockDist = blockDistance(origin, dir, gun.range)

            var bestT = blockDist
            var bestTarget: LivingEntity? = null
            var bestHead = false
            val o = origin.toVector()
            for (target in candidates) {
                val t = intersect(o, dir, target, bestT)
                if (t >= 0.0 && t <= bestT) {
                    bestT = t
                    bestTarget = target
                    val hitY = o.y + dir.y * t
                    bestHead = hitY >= target.location.y + height(target) - s.headHeight
                }
            }

            drawTrail(origin, dir, if (bestTarget != null) bestT else blockDist, gun)

            if (bestTarget != null) {
                val h = hits.getOrPut(bestTarget) { Hit() }
                val mult = if (bestHead) gun.headshotMultiplier else 1.0
                h.damage += gun.damage * mult
                if (bestHead) h.headshot = true
            }
        }

        for ((target, hit) in hits) apply(shooter, target, hit, gun, baseDir)
    }

    // ------------------------------------------------------------------ targets

    private fun candidates(shooter: Player, gun: dev.etran.armed.gun.GunDefinition): List<LivingEntity> {
        val s = plugin.settings
        val r = gun.range
        val out = ArrayList<LivingEntity>()
        val arena = BedWarsHook.arenaOf(shooter)
        for (e: Entity in shooter.getNearbyEntities(r, r, r)) {
            if (e !is LivingEntity || e.isDead || e is ArmorStand) continue
            if (e is Player) {
                if (e.uniqueId == shooter.uniqueId || e.gameMode == GameMode.SPECTATOR) continue
                if (arena != null) {
                    if (!arena.isPlayer(e) || arena.isSpectator(e)) continue
                    if (!s.friendlyFire && BedWarsHook.sameTeam(arena, shooter, e)) continue
                } else if (!s.outsideArenaPvp) continue
            } else if (!s.hitMobs) continue
            out += e
        }
        return out
    }

    private fun height(e: LivingEntity): Double = if (e is Player && e.isSneaking) 1.5 else 1.8

    /** Ray / hitbox test. Returns the distance along the ray, or -1.0 for a miss. */
    private fun intersect(o: Vector, d: Vector, target: LivingEntity, maxT: Double): Double {
        val loc = target.location
        val half = 0.3 + plugin.settings.hitboxExpand
        val e = plugin.settings.hitboxExpand
        val minX = loc.x - half
        val maxX = loc.x + half
        val minY = loc.y - e
        val maxY = loc.y + height(target) + e
        val minZ = loc.z - half
        val maxZ = loc.z + half

        var tMin = 0.0
        var tMax = maxT
        val ro = doubleArrayOf(o.x, o.y, o.z)
        val rd = doubleArrayOf(d.x, d.y, d.z)
        val mins = doubleArrayOf(minX, minY, minZ)
        val maxs = doubleArrayOf(maxX, maxY, maxZ)
        for (i in 0..2) {
            if (Math.abs(rd[i]) < 1.0E-9) {
                if (ro[i] < mins[i] || ro[i] > maxs[i]) return -1.0
            } else {
                var t1 = (mins[i] - ro[i]) / rd[i]
                var t2 = (maxs[i] - ro[i]) / rd[i]
                if (t1 > t2) {
                    val tmp = t1
                    t1 = t2
                    t2 = tmp
                }
                if (t1 > tMin) tMin = t1
                if (t2 < tMax) tMax = t2
                if (tMin > tMax) return -1.0
            }
        }
        return tMin
    }

    // ------------------------------------------------------------------ blocks

    private fun blockDistance(origin: Location, dir: Vector, range: Double): Double {
        val s = plugin.settings
        if (!s.blockCollision) return range
        val world = origin.world ?: return range
        var d = 0.4
        while (d <= range) {
            val x = origin.x + dir.x * d
            val y = origin.y + dir.y * d
            val z = origin.z + dir.z * d
            val bx = Math.floor(x).toInt()
            val bz = Math.floor(z).toInt()
            if (!world.isChunkLoaded(bx shr 4, bz shr 4)) return d
            val type = world.getBlockAt(bx, Math.floor(y).toInt(), bz).type
            if (type.isSolid && type !in s.passThrough) return d
            d += 0.25
        }
        return range
    }

    // ------------------------------------------------------------------ effects

    private fun spread(dir: Vector, degrees: Double): Vector {
        val k = Math.tan(Math.toRadians(degrees))
        return dir.clone().add(
            Vector(
                (random.nextDouble() * 2 - 1) * k,
                (random.nextDouble() * 2 - 1) * k,
                (random.nextDouble() * 2 - 1) * k
            )
        ).normalize()
    }

    private fun drawTrail(origin: Location, dir: Vector, length: Double, gun: GunDefinition) {
        if (gun.trailParticle.equals("none", true)) return
        var d = 1.0
        var points = 0
        while (d < length && points < 60) {
            val at = origin.clone().add(dir.x * d, dir.y * d, dir.z * d)
            Particles.spawn(at, gun.trailParticle, gun.trailCount, gun.trailSpread)
            d += gun.trailStep
            points++
        }
    }

    // ------------------------------------------------------------------ damage

    private fun apply(shooter: Player, target: LivingEntity, hit: Hit, gun: GunDefinition, dir: Vector) {
        val s = plugin.settings
        if (target.isDead) return
        val before = target.health
        if (s.ignoreDamageCooldown) target.noDamageTicks = 0
        target.damage(hit.damage, shooter)

        val landed = target.isDead || target.health < before
        if (!landed) return

        if (gun.fireTicks > 0 && !target.isDead) {
            target.fireTicks = Math.max(target.fireTicks, gun.fireTicks)
        }
        if (gun.knockback > 0.0 && !target.isDead) {
            val flat = Vector(dir.x, 0.0, dir.z)
            if (flat.lengthSquared() > 1.0E-4) {
                flat.normalize().multiply(gun.knockback)
                flat.y = 0.12 * Math.min(gun.knockback, 1.0)
                target.velocity = target.velocity.add(flat)
            }
        }

        if (target.isDead) Sounds.play(shooter, s.killSound)
        else if (hit.headshot && gun.headshotMultiplier > 1.0) Sounds.play(shooter, s.headshotSound)
        else Sounds.play(shooter, s.hitSound)
    }
}
