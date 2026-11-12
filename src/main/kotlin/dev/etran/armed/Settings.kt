package dev.etran.armed

import dev.etran.armed.util.Mats
import dev.etran.armed.util.Sounds
import org.bukkit.Material
import org.bukkit.configuration.file.FileConfiguration

/** Snapshot of the values from config.yml that are read on hot paths. Rebuilt on every reload. */
class Settings(c: FileConfiguration) {
    /**
     * Armed mode applies ONLY to these BedWars1058 arena groups (lower-case name -> lower-case map names).
     * An empty map set means every map of that group. No entries at all means Armed is off everywhere.
     */
    val arenaGroups: Map<String, Set<String>> = run {
        val out = LinkedHashMap<String, Set<String>>()
        val sec = c.getConfigurationSection("arena-groups")
        if (sec != null) {
            for (g in sec.getKeys(false)) {
                out[g.lowercase()] = sec.getStringList(g).map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
            }
        }
        out
    }
    val requireArena = c.getBoolean("settings.require-bedwars-arena", true)
    val outsideArenaPvp = c.getBoolean("settings.outside-arena-pvp", true)
    val friendlyFire = c.getBoolean("settings.friendly-fire", false)
    val hitMobs = c.getBoolean("settings.hit-mobs", false)
    val ignoreDamageCooldown = c.getBoolean("settings.ignore-damage-cooldown", true)
    val hitboxExpand = c.getDouble("settings.hitbox-expand", 0.1).coerceIn(0.0, 2.0)
    val headHeight = c.getDouble("settings.head-height", 0.4).coerceIn(0.05, 1.0)
    val blockCollision = c.getBoolean("settings.block-collision", true)
    val passThrough: Set<Material> = c.getStringList("settings.pass-through-materials").mapNotNull { _root_ide_package_.dev.etran.armed.util.Mats.resolve(it) }.toSet()
    val soundRadius = c.getDouble("settings.sound-radius", 24.0).coerceIn(1.0, 256.0)
    val fireRateToleranceMs = c.getLong("settings.fire-rate-tolerance-ms", 30L).coerceIn(0L, 200L)
    val reloadOnDrop = c.getBoolean("settings.reload-on-drop", true)
    val reloadOnLeftClick = c.getBoolean("settings.reload-on-left-click", false)
    val autoReload = c.getBoolean("settings.auto-reload", true)
    val cancelReloadOnSwitch = c.getBoolean("settings.cancel-reload-on-switch", true)
    val reloadXpBar = c.getBoolean("settings.reload-xp-bar", true)
    val actionBar = c.getBoolean("settings.action-bar", true)
    val cancelGunInteractions = c.getBoolean("settings.cancel-gun-interactions", true)
    val debug = c.getBoolean("settings.debug", false)

    val hitSound: dev.etran.armed.util.Sounds.Spec? = _root_ide_package_.dev.etran.armed.util.Sounds.parse(c.getString("hit-feedback.hit-sound"))
    val headshotSound: dev.etran.armed.util.Sounds.Spec? = _root_ide_package_.dev.etran.armed.util.Sounds.parse(c.getString("hit-feedback.headshot-sound"))
    val killSound: dev.etran.armed.util.Sounds.Spec? = _root_ide_package_.dev.etran.armed.util.Sounds.parse(c.getString("hit-feedback.kill-sound"))
    val purchaseSound: dev.etran.armed.util.Sounds.Spec? = _root_ide_package_.dev.etran.armed.util.Sounds.parse(c.getString("sounds.purchase"))
    val errorSound: dev.etran.armed.util.Sounds.Spec? = _root_ide_package_.dev.etran.armed.util.Sounds.parse(c.getString("sounds.error"))
}
