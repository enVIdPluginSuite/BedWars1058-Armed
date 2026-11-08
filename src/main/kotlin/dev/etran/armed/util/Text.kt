package dev.etran.armed.util

import org.bukkit.ChatColor
import java.util.Locale

object Text {
    private const val CC = '§'
    private val hex = Regex("&#([0-9a-fA-F]{6})")

    fun color(input: String?): String {
        if (input == null) return ""
        var s: String = input
        s = if (Version.supportsHex) {
            hex.replace(s) { m ->
                val sb = StringBuilder().append(CC).append('x')
                for (c in m.groupValues[1]) sb.append(CC).append(c)
                sb.toString()
            }
        } else hex.replace(s, "")
        return ChatColor.translateAlternateColorCodes('&', s)
    }

    fun strip(input: String?): String = ChatColor.stripColor(color(input)) ?: ""

    /** Replaces every {key} in [template]. Values are converted with toString. */
    fun fmt(template: String, vararg pairs: Pair<String, Any?>): String {
        var out = template
        for ((k, v) in pairs) out = out.replace("{$k}", v?.toString() ?: "")
        return out
    }

    /** 4.0 -> "4.0", 0.25 -> "0.25", 1.50 -> "1.5". */
    fun num(d: Double): String {
        var s = String.format(Locale.ROOT, "%.2f", d)
        s = s.trimEnd('0')
        if (s.endsWith(".")) s += "0"
        return s
    }

    fun isValidId(id: String): Boolean = id.isNotEmpty() && id.length <= 32 && id.all { it.isLetterOrDigit() && it.code < 128 || it == '-' || it == '_' }
}
