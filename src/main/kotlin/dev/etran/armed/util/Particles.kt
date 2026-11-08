package dev.etran.armed.util

import org.bukkit.Effect
import org.bukkit.Location
import java.lang.reflect.Method

/**
 * Particles through reflection: org.bukkit.Particle does not exist on 1.8 (World#playEffect with Effect
 * is used there) and many particle names changed in 1.13 and again in 1.20.5, so each name is tried
 * with its known aliases and unknown names are ignored instead of throwing.
 */
object Particles {
    private val groups = listOf(
        listOf("SMOKE_NORMAL", "SMOKE"),
        listOf("EXPLOSION_NORMAL", "POOF"),
        listOf("FIREWORKS_SPARK", "FIREWORK"),
        listOf("VILLAGER_HAPPY", "HAPPY_VILLAGER"),
        listOf("CRIT_MAGIC", "ENCHANTED_HIT"),
        listOf("FLAME", "FLAME"),
        listOf("CRIT", "CRIT")
    )

    /** Legacy 1.8 Effect names for the particle names above. */
    private val legacyEffects = mapOf(
        "SMOKE_NORMAL" to "SMALL_SMOKE", "SMOKE" to "SMALL_SMOKE",
        "EXPLOSION_NORMAL" to "EXPLOSION", "POOF" to "EXPLOSION",
        "FIREWORKS_SPARK" to "FIREWORKS_SPARK", "FIREWORK" to "FIREWORKS_SPARK",
        "VILLAGER_HAPPY" to "HAPPY_VILLAGER", "HAPPY_VILLAGER" to "HAPPY_VILLAGER",
        "CRIT_MAGIC" to "MAGIC_CRIT", "ENCHANTED_HIT" to "MAGIC_CRIT",
        "FLAME" to "FLAME", "CRIT" to "CRIT"
    )

    private var particleClass: Class<*>? = null
    private var valueOf: Method? = null
    private var spawn: Method? = null
    private var ready = false
    private val cache = HashMap<String, Any?>()
    private val legacyCache = HashMap<String, Effect?>()

    private fun init() {
        ready = true
        if (Version.isLegacy) return
        try {
            val pc = Class.forName("org.bukkit.Particle")
            particleClass = pc
            valueOf = pc.getMethod("valueOf", String::class.java)
            val dbl = Double::class.javaPrimitiveType
            spawn = org.bukkit.World::class.java.getMethod(
                "spawnParticle", pc, Location::class.java, Int::class.javaPrimitiveType, dbl, dbl, dbl, dbl
            )
        } catch (_: Throwable) {
            particleClass = null
            spawn = null
        }
    }

    private fun candidates(name: String): List<String> {
        val n = name.uppercase()
        val g = groups.firstOrNull { n in it }
        return if (g != null) listOf(n) + g.filter { it != n } else listOf(n)
    }

    private fun modern(name: String): Any? = cache.getOrPut(name) {
        for (c in candidates(name)) {
            try {
                return@getOrPut valueOf!!.invoke(null, c)
            } catch (_: Throwable) {
            }
        }
        null
    }

    private fun legacy(name: String): Effect? = legacyCache.getOrPut(name) {
        val mapped = legacyEffects[name.uppercase()] ?: name.uppercase()
        try {
            Effect.valueOf(mapped)
        } catch (_: Throwable) {
            null
        }
    }

    fun spawn(at: Location, name: String?, count: Int, spread: Double) {
        if (name == null || name.isEmpty() || name.equals("none", true)) return
        if (!ready) init()
        val world = at.world ?: return
        try {
            if (Version.isLegacy) {
                val e = legacy(name) ?: return
                world.playEffect(at, e, 0)
            } else {
                val p = modern(name) ?: return
                spawn?.invoke(world, p, at, count, spread, spread, spread, 0.0)
            }
        } catch (_: Throwable) {
        }
    }
}
