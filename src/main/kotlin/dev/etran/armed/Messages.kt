package dev.etran.armed

import dev.etran.armed.util.Text
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.FileConfiguration

class Messages(private val cfg: FileConfiguration) {
    val prefix: String get() = _root_ide_package_.dev.etran.armed.util.Text.color(cfg.getString("prefix") ?: "")

    private fun raw(key: String): String = cfg.getString(key) ?: "&c[missing message: $key]"

    /** Coloured message without prefix. */
    fun get(key: String, vararg pairs: Pair<String, Any?>): String = _root_ide_package_.dev.etran.armed.util.Text.color(
        _root_ide_package_.dev.etran.armed.util.Text.fmt(raw(key), *pairs))

    fun send(to: CommandSender, key: String, vararg pairs: Pair<String, Any?>) {
        val m = get(key, *pairs)
        if (m.isNotEmpty()) to.sendMessage(prefix + m)
    }

    /** Sends without the prefix (for lists / help output). */
    fun sendRaw(to: CommandSender, text: String) = to.sendMessage(_root_ide_package_.dev.etran.armed.util.Text.color(text))
}
