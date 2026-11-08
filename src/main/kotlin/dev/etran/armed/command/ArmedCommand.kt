package dev.etran.armed.command

import dev.etran.armed.ArmedPlugin
import dev.etran.armed.gun.GunProperties
import dev.etran.armed.gun.PropType
import dev.etran.armed.util.Text
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class ArmedCommand(private val plugin: ArmedPlugin) : CommandExecutor, TabCompleter {
    private val m get() = plugin.messages

    private val subcommands = listOf(
        "help", "shop", "list", "info", "give", "reload", "set", "create", "delete", "description", "config"
    )

    private fun perm(sender: CommandSender, node: String): Boolean {
        if (sender.hasPermission(node) || sender.hasPermission("armed.admin")) return true
        m.send(sender, "no-permission")
        return false
    }

    private fun canSee(sender: CommandSender, sub: String): Boolean = when (sub) {
        "help", "shop" -> true
        "list", "info" -> sender.hasPermission("armed.use")
        "reload" -> sender.hasPermission("armed.admin.reload") || sender.hasPermission("armed.admin")
        "give" -> sender.hasPermission("armed.admin.give") || sender.hasPermission("armed.admin")
        else -> sender.hasPermission("armed.admin.edit") || sender.hasPermission("armed.admin")
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        val sub = args.getOrNull(0)?.lowercase() ?: "help"
        when (sub) {
            "help", "?" -> help(sender, label)
            "shop" -> {
                if (sender !is Player) return m.send(sender, "players-only").let { true }
                plugin.shop.open(sender)
            }
            "list" -> list(sender)
            "info" -> info(sender, args, label)
            "give" -> if (perm(sender, "armed.admin.give")) give(sender, args, label)
            "reload" -> if (perm(sender, "armed.admin.reload")) reload(sender)
            "set" -> if (perm(sender, "armed.admin.edit")) set(sender, args, label)
            "create" -> if (perm(sender, "armed.admin.edit")) create(sender, args, label)
            "delete" -> if (perm(sender, "armed.admin.edit")) delete(sender, args, label)
            "description", "desc" -> if (perm(sender, "armed.admin.edit")) description(sender, args, label)
            "config" -> if (perm(sender, "armed.admin.edit")) config(sender, args, label)
            else -> m.send(sender, "unknown-subcommand")
        }
        return true
    }

    private fun usage(sender: CommandSender, text: String) = m.send(sender, "usage", "usage" to text)

    // ------------------------------------------------------------------ simple commands

    private fun help(sender: CommandSender, label: String) {
        m.sendRaw(sender, m.get("help-header"))
        val lines = listOf(
            "shop" to "Open the gun shop",
            "list" to "List all guns",
            "info <gun>" to "Show every property of a gun",
            "give <player> <gun>" to "Give a gun to a player",
            "reload" to "Reload config.yml, messages.yml and guns.yml",
            "set <gun> <property> <value>" to "Edit a gun (tab complete the property)",
            "create <id> [copyFrom]" to "Create a new gun, optionally copied from another",
            "delete <id>" to "Delete a gun",
            "description <gun> add|remove|clear [text]" to "Edit the gun description lines",
            "config <path> [value]" to "View or change a setting from config.yml"
        )
        for ((cmd, desc) in lines) {
            val sub = cmd.substringBefore(' ')
            if (canSee(sender, sub)) m.sendRaw(sender, "&e/$label $cmd &8- &7$desc")
        }
    }

    private fun list(sender: CommandSender) {
        val guns = plugin.registry.all()
        m.sendRaw(sender, m.get("gun-list-header", "count" to guns.size))
        val free = m.get("shop-cost-free")
        for (g in guns) {
            m.sendRaw(
                sender,
                m.get(
                    "gun-list-entry",
                    "id" to g.id,
                    "name" to Text.color(g.displayName),
                    "damage" to Text.num(g.damage),
                    "cost" to Text.color(plugin.currencies.costText(g, free))
                )
            )
        }
    }

    private fun info(sender: CommandSender, args: Array<String>, label: String) {
        val id = args.getOrNull(1) ?: return usage(sender, "/$label info <gun>")
        val g = plugin.registry.get(id) ?: return m.send(sender, "unknown-gun", "gun" to id)
        m.sendRaw(sender, m.get("gun-info-header", "id" to g.id))
        for ((k, v) in plugin.registry.describe(g)) {
            m.sendRaw(sender, m.get("gun-info-line", "key" to k, "value" to v))
        }
    }

    private fun give(sender: CommandSender, args: Array<String>, label: String) {
        if (args.size < 3) return usage(sender, "/$label give <player> <gun>")
        val target = plugin.server.getPlayerExact(args[1]) ?: return m.send(sender, "unknown-player", "player" to args[1])
        val g = plugin.registry.get(args[2]) ?: return m.send(sender, "unknown-gun", "gun" to args[2])
        if (!plugin.controller.giveGun(target, g)) return m.send(sender, "inventory-full")
        val name = Text.color(g.displayName)
        m.send(sender, "gave-gun", "name" to name, "player" to target.name)
        if (sender != target) m.send(target, "received-gun", "name" to name)
    }

    private fun reload(sender: CommandSender) {
        try {
            val n = plugin.reloadAll()
            m.send(sender, "reloaded", "count" to n)
        } catch (e: Exception) {
            m.send(sender, "reload-failed", "error" to (e.message ?: e.javaClass.simpleName))
            plugin.logger.warning("Reload failed: ${e.message}")
        }
    }

    // ------------------------------------------------------------------ editing

    private fun set(sender: CommandSender, args: Array<String>, label: String) {
        if (args.size < 3) return usage(sender, "/$label set <gun> <property> <value>")
        val g = plugin.registry.get(args[1]) ?: return m.send(sender, "unknown-gun", "gun" to args[1])
        val prop = GunProperties.find(args[2]) ?: return m.send(sender, "unknown-property", "property" to args[2])
        if (args.size < 4) return usage(sender, "/$label set ${g.id} ${prop.name} <value>")

        var raw = args.drop(3).joinToString(" ")
        if (prop.type == PropType.MATERIAL && raw.equals("hand", true)) {
            if (sender !is Player) return m.send(sender, "players-only")
            val held = sender.inventory.getItem(sender.inventory.heldItemSlot)
            if (plugin.items.isEmpty(held)) return m.send(sender, "need-item-in-hand")
            raw = held!!.type.name
        }
        val value = try {
            GunProperties.parse(prop, raw, plugin.currencies.keys())
        } catch (e: IllegalArgumentException) {
            return m.send(sender, "gun-set-invalid", "error" to (e.message ?: "invalid"))
        }
        plugin.registry.setProperty(g.id, prop, value)
        m.send(sender, "gun-set", "property" to prop.name, "gun" to g.id, "value" to value)
    }

    private fun create(sender: CommandSender, args: Array<String>, label: String) {
        val id = args.getOrNull(1) ?: return usage(sender, "/$label create <id> [copyFrom]")
        if (!Text.isValidId(id)) return m.send(sender, "invalid-id")
        val from = args.getOrNull(2)
        if (from != null && plugin.registry.get(from) == null) return m.send(sender, "unknown-gun", "gun" to from)
        if (!plugin.registry.create(id, from)) return m.send(sender, "gun-exists", "gun" to id.lowercase())
        m.send(sender, "gun-created", "gun" to id.lowercase())
    }

    private fun delete(sender: CommandSender, args: Array<String>, label: String) {
        val id = args.getOrNull(1) ?: return usage(sender, "/$label delete <gun>")
        if (!plugin.registry.delete(id)) return m.send(sender, "unknown-gun", "gun" to id)
        m.send(sender, "gun-deleted", "gun" to id.lowercase())
    }

    private fun description(sender: CommandSender, args: Array<String>, label: String) {
        val u = "/$label description <gun> add <text> | remove <line> | clear"
        if (args.size < 3) return usage(sender, u)
        val g = plugin.registry.get(args[1]) ?: return m.send(sender, "unknown-gun", "gun" to args[1])
        when (args[2].lowercase()) {
            "add" -> {
                if (args.size < 4) return usage(sender, u)
                plugin.registry.addDescription(g.id, args.drop(3).joinToString(" "))
                m.send(sender, "description-added", "gun" to g.id)
            }
            "remove" -> {
                val idx = args.getOrNull(3)?.toIntOrNull() ?: return usage(sender, u)
                if (!plugin.registry.removeDescription(g.id, idx)) return m.send(sender, "description-bad-index", "index" to idx)
                m.send(sender, "description-removed", "gun" to g.id, "index" to idx)
            }
            "clear" -> {
                plugin.registry.clearDescription(g.id)
                m.send(sender, "description-cleared", "gun" to g.id)
            }
            else -> usage(sender, u)
        }
    }

    private fun config(sender: CommandSender, args: Array<String>, label: String) {
        val path = args.getOrNull(1) ?: return usage(sender, "/$label config <path> [value]")
        val cfg = plugin.configs.config
        if (!cfg.contains(path)) return m.send(sender, "config-unknown-path", "path" to path)
        val current = cfg.get(path)
        if (current is org.bukkit.configuration.ConfigurationSection || current is List<*>) {
            return m.send(sender, "config-not-scalar")
        }
        if (args.size < 3) return m.send(sender, "config-value", "path" to path, "value" to current)

        val raw = args.drop(2).joinToString(" ")
        val parsed: Any = when (current) {
            is Boolean -> raw.lowercase().let { if (it == "true" || it == "on") true else if (it == "false" || it == "off") false else return m.send(sender, "gun-set-invalid", "error" to "expected true/false") }
            is Int -> raw.toIntOrNull() ?: return m.send(sender, "gun-set-invalid", "error" to "expected a whole number")
            is Long -> raw.toLongOrNull() ?: return m.send(sender, "gun-set-invalid", "error" to "expected a whole number")
            is Double -> raw.toDoubleOrNull() ?: return m.send(sender, "gun-set-invalid", "error" to "expected a number")
            else -> raw
        }
        cfg.set(path, parsed)
        try {
            plugin.configs.saveConfig()
        } catch (e: Exception) {
            return m.send(sender, "reload-failed", "error" to (e.message ?: "could not save"))
        }
        plugin.rebuild()
        m.send(sender, "config-set", "path" to path, "value" to parsed)
    }

    // ------------------------------------------------------------------ tab completion

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        val last = args.lastOrNull()?.lowercase() ?: ""
        fun filter(options: Collection<String>) = options.filter { it.lowercase().startsWith(last) }.sorted()

        if (args.size <= 1) return filter(subcommands.filter { canSee(sender, it) })
        val sub = args[0].lowercase()
        if (!canSee(sender, sub)) return emptyList()
        val guns = plugin.registry.ids()

        return when (sub) {
            "info", "delete" -> if (args.size == 2) filter(guns) else emptyList()
            "give" -> when (args.size) {
                2 -> filter(plugin.server.onlinePlayers.map { it.name })
                3 -> filter(guns)
                else -> emptyList()
            }
            "create" -> if (args.size == 3) filter(guns) else emptyList()
            "set" -> when (args.size) {
                2 -> filter(guns)
                3 -> filter(GunProperties.all.map { it.name })
                4 -> filter(valueHints(args[2]))
                else -> emptyList()
            }
            "description", "desc" -> when (args.size) {
                2 -> filter(guns)
                3 -> filter(listOf("add", "remove", "clear"))
                else -> emptyList()
            }
            "config" -> if (args.size == 2) filter(configPaths()) else if (args.size == 3) filter(configValueHints(args[1])) else emptyList()
            else -> emptyList()
        }
    }

    private fun valueHints(property: String): List<String> {
        val p = GunProperties.find(property) ?: return emptyList()
        return when (p.type) {
            PropType.BOOL -> listOf("true", "false")
            PropType.CURRENCY -> plugin.currencies.keys().toList()
            PropType.MATERIAL -> listOf("hand", "WOODEN_HOE", "STONE_HOE", "IRON_HOE", "GOLDEN_HOE", "DIAMOND_HOE", "BLAZE_ROD", "STICK", "BOW")
            PropType.SOUND -> listOf("none", "entity.arrow.shoot 1.0 1.0", "entity.generic.explode 1.0 1.0")
            PropType.STRING -> when (p.name) {
                "trail-particle" -> listOf("none", "CRIT", "FLAME", "SMOKE_NORMAL", "FIREWORKS_SPARK")
                "duel", "horde", "skill" -> listOf("Low", "Medium", "High", "Extreme")
                "permission" -> listOf("none")
                else -> emptyList()
            }
            else -> emptyList()
        }
    }

    private fun configPaths(): List<String> {
        val cfg = plugin.configs.config
        return cfg.getKeys(true).filter { k ->
            val v = cfg.get(k)
            v != null && v !is org.bukkit.configuration.ConfigurationSection && v !is List<*>
        }
    }

    private fun configValueHints(path: String): List<String> =
        if (plugin.configs.config.get(path) is Boolean) listOf("true", "false") else emptyList()
}
