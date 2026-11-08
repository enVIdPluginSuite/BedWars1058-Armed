package dev.etran.armed.gun

import dev.etran.armed.util.Mats

enum class PropType { DOUBLE, INT, BOOL, STRING, MATERIAL, SOUND, CURRENCY }

/** One editable gun property: the name used by /armed set, the YAML path, and its validation rules. */
class GunProperty(
    val name: String,
    val path: String,
    val type: PropType,
    val min: Double = Double.NEGATIVE_INFINITY,
    val max: Double = Double.POSITIVE_INFINITY
)

object GunProperties {
    val all: List<GunProperty> = listOf(
        GunProperty("display-name", "display-name", PropType.STRING),
        GunProperty("material", "material", PropType.MATERIAL),
        GunProperty("starter", "starter", PropType.BOOL),
        GunProperty("permission", "permission", PropType.STRING),
        GunProperty("hotbar-slot", "hotbar-slot", PropType.INT, -1.0, 8.0),
        GunProperty("damage", "stats.damage", PropType.DOUBLE, 0.0, 1000.0),
        GunProperty("headshot-multiplier", "stats.headshot-multiplier", PropType.DOUBLE, 0.0, 100.0),
        GunProperty("fire-rate", "stats.fire-rate", PropType.DOUBLE, 0.05, 60.0),
        GunProperty("burst-size", "stats.burst-size", PropType.INT, 1.0, 20.0),
        GunProperty("clip-size", "stats.clip-size", PropType.INT, 1.0, 1000.0),
        GunProperty("reload-time", "stats.reload-time", PropType.DOUBLE, 0.0, 120.0),
        GunProperty("reserve-ammo", "stats.reserve-ammo", PropType.INT, -1.0, 100000.0),
        GunProperty("range", "stats.range", PropType.DOUBLE, 1.0, 200.0),
        GunProperty("pellets", "stats.pellets", PropType.INT, 1.0, 64.0),
        GunProperty("spread", "stats.spread", PropType.DOUBLE, 0.0, 90.0),
        GunProperty("knockback", "stats.knockback", PropType.DOUBLE, 0.0, 10.0),
        GunProperty("fire-ticks", "stats.fire-ticks", PropType.INT, 0.0, 1200.0),
        GunProperty("cost-currency", "cost.currency", PropType.CURRENCY),
        GunProperty("cost-amount", "cost.amount", PropType.INT, 0.0, 2304.0),
        GunProperty("shop-enabled", "shop.enabled", PropType.BOOL),
        GunProperty("shop-slot", "shop.slot", PropType.INT, -1.0, 53.0),
        GunProperty("duel", "ratings.duel", PropType.STRING),
        GunProperty("horde", "ratings.horde", PropType.STRING),
        GunProperty("skill", "ratings.skill", PropType.STRING),
        GunProperty("sound-shoot", "sounds.shoot", PropType.SOUND),
        GunProperty("sound-reload-start", "sounds.reload-start", PropType.SOUND),
        GunProperty("sound-reload-end", "sounds.reload-end", PropType.SOUND),
        GunProperty("sound-empty", "sounds.empty", PropType.SOUND),
        GunProperty("trail-particle", "trail.particle", PropType.STRING),
        GunProperty("trail-step", "trail.step", PropType.DOUBLE, 0.2, 20.0),
        GunProperty("trail-count", "trail.count", PropType.INT, 1.0, 50.0),
        GunProperty("trail-spread", "trail.spread", PropType.DOUBLE, 0.0, 2.0)
    )

    private val byName = all.associateBy { it.name }

    fun find(name: String): GunProperty? = byName[name.lowercase()]

    /**
     * Parses [raw] for [prop]. Returns the value to store in YAML.
     * Throws IllegalArgumentException with a short human message when the value is not acceptable.
     */
    fun parse(prop: GunProperty, raw: String, currencyKeys: Collection<String>): Any {
        val text = raw.trim()
        return when (prop.type) {
            PropType.DOUBLE -> {
                val d = text.toDoubleOrNull() ?: throw IllegalArgumentException("'$text' is not a number")
                requireRange(prop, d)
                d
            }
            PropType.INT -> {
                val i = text.toIntOrNull() ?: throw IllegalArgumentException("'$text' is not a whole number")
                requireRange(prop, i.toDouble())
                i
            }
            PropType.BOOL -> when (text.lowercase()) {
                "true", "yes", "on" -> true
                "false", "no", "off" -> false
                else -> throw IllegalArgumentException("'$text' is not true/false")
            }
            PropType.MATERIAL -> {
                val m = Mats.resolve(text.substringBefore(':')) ?: throw IllegalArgumentException("'$text' is not a material on this server")
                // keep an optional ":data" suffix (only honoured on pre-1.13 servers)
                if (text.contains(':')) m.name + ":" + text.substringAfter(':') else m.name
            }
            PropType.CURRENCY -> {
                val key = text.lowercase()
                if (key !in currencyKeys) throw IllegalArgumentException("unknown currency '$text' (have: ${currencyKeys.joinToString()})")
                key
            }
            PropType.SOUND -> if (text.equals("none", true)) "none" else text
            PropType.STRING -> if (prop.name == "permission" && text.equals("none", true)) "" else text
        }
    }

    private fun requireRange(prop: GunProperty, v: Double) {
        if (v < prop.min || v > prop.max) {
            throw IllegalArgumentException("must be between ${trim(prop.min)} and ${trim(prop.max)}")
        }
    }

    private fun trim(d: Double): String = if (d == Math.floor(d)) d.toLong().toString() else d.toString()
}
