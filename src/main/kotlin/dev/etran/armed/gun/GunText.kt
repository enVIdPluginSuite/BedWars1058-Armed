package dev.etran.armed.gun

import dev.etran.armed.ArmedPlugin
import dev.etran.armed.util.Text

/** Placeholder expansion shared by the in-hand item lore and the shop lore. */
object GunText {
    fun placeholders(plugin: ArmedPlugin, g: GunDefinition): Array<Pair<String, Any?>> {
        val free = plugin.messages.get("shop-cost-free")
        return arrayOf(
            "id" to g.id,
            "name" to g.displayName,
            "damage" to Text.num(g.damage),
            "headshot" to Text.num(g.headshotMultiplier),
            "fire_rate" to Text.num(g.fireRate),
            "clip" to g.clipSize,
            "reload" to Text.num(g.reloadTime),
            "range" to Text.num(g.range),
            "pellets" to g.pellets,
            "pellets_suffix" to (if (g.pellets > 1) " &8x${g.pellets}" else ""),
            "duel" to g.duel,
            "horde" to g.horde,
            "skill" to g.skill,
            "cost" to plugin.currencies.costText(g, free),
            "reserve" to (if (g.reserveLimited) g.reserveAmmo.toString() else "∞")
        )
    }

    /**
     * Expands [template]. A line that is exactly "{description}" is replaced by the gun's description,
     * every other line gets the placeholders in [extra] and [placeholders] replaced.
     */
    fun lines(plugin: ArmedPlugin, template: List<String>, g: GunDefinition, vararg extra: Pair<String, Any?>): List<String> {
        val out = ArrayList<String>()
        val base = placeholders(plugin, g)
        for (line in template) {
            if (line.trim() == "{description}") {
                for (d in g.description) out += Text.color(d)
                continue
            }
            out += Text.color(Text.fmt(Text.fmt(line, *base), *extra))
        }
        return out
    }
}
