package dev.etran.armed

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.InputStreamReader

/** Owns config.yml, messages.yml and guns.yml. */
class ConfigManager(private val plugin: dev.etran.armed.ArmedPlugin) {
    lateinit var config: YamlConfiguration private set
    lateinit var messages: YamlConfiguration private set
    lateinit var guns: YamlConfiguration private set

    private fun file(name: String) = File(plugin.dataFolder, name)

    /** Loads all three files. Throws (and changes nothing) if any file has a YAML error. */
    fun loadAll() {
        plugin.dataFolder.mkdirs()
        for (name in listOf("config.yml", "messages.yml", "guns.yml")) {
            if (!file(name).exists()) plugin.saveResource(name, false)
        }
        val c = read("config.yml", true)
        val m = read("messages.yml", true)
        val g = read("guns.yml", false) // no defaults: deleted guns must stay deleted
        config = c
        messages = m
        guns = g
    }

    private fun read(name: String, withDefaults: Boolean): YamlConfiguration {
        val cfg = YamlConfiguration()
        try {
            cfg.load(file(name))
        } catch (e: Exception) {
            throw IllegalStateException("$name: ${e.message?.lineSequence()?.firstOrNull() ?: e.javaClass.simpleName}", e)
        }
        if (withDefaults) {
            plugin.getResource(name)?.use { stream ->
                cfg.setDefaults(YamlConfiguration.loadConfiguration(InputStreamReader(stream, Charsets.UTF_8)))
            }
        }
        return cfg
    }

    fun saveConfig() = config.save(file("config.yml"))
    fun saveGuns() = guns.save(file("guns.yml"))
}
