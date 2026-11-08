package dev.etran.armed.gun

import dev.etran.armed.ArmedPlugin
import dev.etran.armed.util.Mats
import dev.etran.armed.util.Text
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration

/** All guns, plus the operations behind the in-game edit commands (each one saves guns.yml). */
class GunRegistry(private val plugin: ArmedPlugin) {
    private var guns = LinkedHashMap<String, GunDefinition>()

    fun all(): Collection<GunDefinition> = guns.values
    fun ids(): List<String> = guns.keys.toList()
    fun get(id: String?): GunDefinition? = if (id == null) null else guns[id.lowercase()]

    fun rebuild(cfg: FileConfiguration) {
        val out = LinkedHashMap<String, GunDefinition>()
        val sec = cfg.getConfigurationSection("guns")
        if (sec != null) {
            for (key in sec.getKeys(false)) {
                val s = sec.getConfigurationSection(key) ?: continue
                val id = key.lowercase()
                try {
                    val g = GunDefinition.fromSection(id, s)
                    if (Mats.resolve(g.material.substringBefore(':')) == null) {
                        plugin.logger.warning("Gun '$id': unknown material '${g.material}', using a stick.")
                    }
                    if (!g.free && plugin.currencies.get(g.costCurrency) == null) {
                        plugin.logger.warning("Gun '$id': unknown currency '${g.costCurrency}', the gun will be free.")
                    }
                    out[id] = g
                } catch (e: Exception) {
                    plugin.logger.warning("Gun '$key' could not be loaded: ${e.message}")
                }
            }
        }
        guns = out
    }

    private fun section(id: String): ConfigurationSection? = plugin.configs.guns.getConfigurationSection("guns.${id.lowercase()}")

    private fun commit() {
        plugin.configs.saveGuns()
        plugin.rebuild()
    }

    fun setProperty(id: String, prop: GunProperty, value: Any) {
        if (section(id) == null) return
        plugin.configs.guns.set("guns.${id.lowercase()}.${prop.path}", value)
        commit()
    }

    /** Creates [id] as a copy of [copyFrom] (or of built-in defaults when null). */
    fun create(id: String, copyFrom: String?): Boolean {
        val key = id.lowercase()
        if (section(key) != null) return false
        val cfg = plugin.configs.guns
        val src = copyFrom?.let { section(it) }
        if (src != null) {
            for ((path, v) in src.getValues(true)) {
                if (v is ConfigurationSection) continue
                cfg.set("guns.$key.$path", v)
            }
        } else {
            val d = GunDefinition.fromSection(key, cfg.createSection("guns.$key"))
            cfg.set("guns.$key.material", d.material)
            cfg.set("guns.$key.stats.damage", d.damage)
            cfg.set("guns.$key.stats.fire-rate", d.fireRate)
            cfg.set("guns.$key.stats.clip-size", d.clipSize)
            cfg.set("guns.$key.stats.reload-time", d.reloadTime)
            cfg.set("guns.$key.stats.range", d.range)
            cfg.set("guns.$key.cost.currency", "iron")
            cfg.set("guns.$key.cost.amount", 10)
        }
        val pretty = key.replace('-', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() }
        cfg.set("guns.$key.display-name", "&f$pretty")
        cfg.set("guns.$key.starter", false)
        cfg.set("guns.$key.shop.enabled", true)
        cfg.set("guns.$key.shop.slot", -1)
        cfg.set("guns.$key.description", ArrayList<String>())
        commit()
        return true
    }

    fun delete(id: String): Boolean {
        if (section(id) == null) return false
        plugin.configs.guns.set("guns.${id.lowercase()}", null)
        commit()
        return true
    }

    fun addDescription(id: String, line: String): Boolean {
        val s = section(id) ?: return false
        val list = ArrayList(s.getStringList("description"))
        list.add(line)
        s.set("description", list)
        commit()
        return true
    }

    /** [index] is 1-based. */
    fun removeDescription(id: String, index: Int): Boolean {
        val s = section(id) ?: return false
        val list = ArrayList(s.getStringList("description"))
        if (index < 1 || index > list.size) return false
        list.removeAt(index - 1)
        s.set("description", list)
        commit()
        return true
    }

    fun clearDescription(id: String): Boolean {
        val s = section(id) ?: return false
        s.set("description", ArrayList<String>())
        commit()
        return true
    }

    /** Reads the current stored value of [prop] (for /armed info). */
    fun valueOf(g: GunDefinition, prop: GunProperty): String {
        val v = section(g.id)?.get(prop.path)
        return v?.toString() ?: "-"
    }

    fun describe(g: GunDefinition): List<Pair<String, String>> {
        val lines = ArrayList<Pair<String, String>>()
        for (p in GunProperties.all) lines += p.name to valueOf(g, p)
        lines += "description" to (if (g.description.isEmpty()) "-" else g.description.joinToString(" | ") { Text.strip(it) })
        return lines
    }
}
