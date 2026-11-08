package dev.etran.armed

import dev.etran.armed.bedwars.BedWarsHook
import dev.etran.armed.bedwars.BedWarsListener
import dev.etran.armed.command.ArmedCommand
import dev.etran.armed.combat.Ballistics
import dev.etran.armed.combat.GunListener
import dev.etran.armed.gun.Currencies
import dev.etran.armed.gun.GunController
import dev.etran.armed.gun.GunItems
import dev.etran.armed.gun.GunRegistry
import dev.etran.armed.gun.GunStates
import dev.etran.armed.shop.ShopListener
import dev.etran.armed.shop.ShopMenu
import dev.etran.armed.util.Version
import org.bukkit.plugin.java.JavaPlugin

class ArmedPlugin : JavaPlugin() {
    companion object {
        lateinit var instance: ArmedPlugin
            private set
    }

    lateinit var configs: dev.etran.armed.ConfigManager private set
    lateinit var settings: dev.etran.armed.Settings private set
    lateinit var messages: dev.etran.armed.Messages private set
    lateinit var currencies: dev.etran.armed.gun.Currencies private set
    lateinit var registry: dev.etran.armed.gun.GunRegistry private set
    lateinit var states: dev.etran.armed.gun.GunStates private set
    lateinit var items: dev.etran.armed.gun.GunItems private set
    lateinit var ballistics: dev.etran.armed.combat.Ballistics private set
    lateinit var controller: dev.etran.armed.gun.GunController private set
    lateinit var shop: dev.etran.armed.shop.ShopMenu private set

    override fun onEnable() {
        instance = this

        if (!_root_ide_package_.dev.etran.armed.bedwars.BedWarsHook.init(logger)) {
            logger.severe("BedWars1058 API not found. Disabling.")
            server.pluginManager.disablePlugin(this)
            return
        }

        configs = _root_ide_package_.dev.etran.armed.ConfigManager(this)
        try {
            configs.loadAll()
        } catch (e: Exception) {
            logger.severe("Could not load configuration: ${e.message}")
            server.pluginManager.disablePlugin(this)
            return
        }

        states = _root_ide_package_.dev.etran.armed.gun.GunStates(this)
        registry = _root_ide_package_.dev.etran.armed.gun.GunRegistry(this)
        items = _root_ide_package_.dev.etran.armed.gun.GunItems(this)
        ballistics = _root_ide_package_.dev.etran.armed.combat.Ballistics(this)
        controller = _root_ide_package_.dev.etran.armed.gun.GunController(this)
        shop = _root_ide_package_.dev.etran.armed.shop.ShopMenu(this)
        rebuild()

        val pm = server.pluginManager
        pm.registerEvents(_root_ide_package_.dev.etran.armed.combat.GunListener(this), this)
        pm.registerEvents(_root_ide_package_.dev.etran.armed.shop.ShopListener(this), this)
        pm.registerEvents(_root_ide_package_.dev.etran.armed.bedwars.BedWarsListener(this), this)

        val command = _root_ide_package_.dev.etran.armed.command.ArmedCommand(this)
        getCommand("armed")?.let {
            it.executor = command
            it.tabCompleter = command
        }

        logger.info("Enabled on Minecraft ${_root_ide_package_.dev.etran.armed.util.Version} with ${registry.all().size} gun(s).")
    }

    override fun onDisable() {
        if (::states.isInitialized) states.clearAll()
    }

    /** Rebuilds everything derived from the already loaded YAML files. */
    fun rebuild() {
        settings = _root_ide_package_.dev.etran.armed.Settings(configs.config)
        messages = _root_ide_package_.dev.etran.armed.Messages(configs.messages)
        currencies = _root_ide_package_.dev.etran.armed.gun.Currencies(configs.config, logger)
        registry.rebuild(configs.guns)
        items.refreshAll()
    }

    /** Re-reads the files from disk. Returns the number of guns. Throws if a file is invalid. */
    fun reloadAll(): Int {
        configs.loadAll()
        rebuild()
        return registry.all().size
    }

    fun debug(msg: String) {
        if (::settings.isInitialized && settings.debug) logger.info("[debug] $msg")
    }
}
