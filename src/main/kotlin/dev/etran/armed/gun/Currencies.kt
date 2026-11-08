package dev.etran.armed.gun

import dev.etran.armed.util.Mats
import dev.etran.armed.util.Text
import org.bukkit.Material
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import java.util.logging.Logger

class Currency(val key: String, val material: Material, val display: String)

/** Resources a gun can cost, defined in config.yml under "currencies". Paid from the player's inventory. */
class Currencies(cfg: FileConfiguration, log: Logger) {
    private val map = LinkedHashMap<String, Currency>()

    init {
        val sec = cfg.getConfigurationSection("currencies")
        if (sec != null) {
            for (key in sec.getKeys(false)) {
                val matName = sec.getString("$key.material")
                val mat = Mats.resolve(matName)
                if (mat == null) {
                    log.warning("Currency '$key' has an unknown material '$matName' and was skipped.")
                    continue
                }
                map[key.lowercase()] = Currency(key.lowercase(), mat, sec.getString("$key.display") ?: key)
            }
        }
    }

    fun keys(): Set<String> = map.keys
    fun get(key: String?): Currency? = key?.let { map[it.lowercase()] }

    fun count(player: Player, c: Currency): Int {
        var total = 0
        for (item in player.inventory.contents) {
            if (item != null && item.type == c.material) total += item.amount
        }
        return total
    }

    /** Removes [amount] of the currency. Caller must have checked [count] first. */
    fun take(player: Player, c: Currency, amount: Int) {
        var left = amount
        val inv = player.inventory
        for (i in 0 until inv.size) {
            if (left <= 0) break
            val item = inv.getItem(i) ?: continue
            if (item.type != c.material) continue
            if (item.amount <= left) {
                left -= item.amount
                inv.setItem(i, null)
            } else {
                item.amount = item.amount - left
                inv.setItem(i, item)
                left = 0
            }
        }
        refresh(player)
    }

    fun give(player: Player, c: Currency, amount: Int) {
        var left = amount
        while (left > 0) {
            val n = Math.min(left, 64)
            val overflow = player.inventory.addItem(org.bukkit.inventory.ItemStack(c.material, n))
            for (rest in overflow.values) player.world.dropItemNaturally(player.location, rest)
            left -= n
        }
        refresh(player)
    }

    /** Human readable cost with currency colours, e.g. "&f6 &6Gold". Callers supply the "free" text. */
    fun costText(gun: GunDefinition, freeText: String): String {
        if (gun.free) return freeText
        val c = get(gun.costCurrency)
        return "&f${gun.costAmount} " + (c?.display ?: gun.costCurrency)
    }

    companion object {
        @Suppress("DEPRECATION")
        fun refresh(player: Player) {
            try {
                player.updateInventory()
            } catch (_: Throwable) {
            }
        }
    }

    fun plainName(c: Currency): String = Text.strip(c.display)
}
