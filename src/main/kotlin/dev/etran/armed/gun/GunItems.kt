package dev.etran.armed.gun

import dev.etran.armed.ArmedPlugin
import dev.etran.armed.util.Mats
import dev.etran.armed.util.Text
import dev.etran.armed.util.Version
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.lang.reflect.Method

/**
 * Creates gun items and recognises them again.
 *
 * Identification, in order:
 *  1. PersistentDataContainer (1.14+), via reflection because the compile baseline is 1.8.
 *  2. A hidden colour-code marker at the end of the display name (1.8 - 1.12, where names are stored verbatim).
 *  3. Matching the display name text (1.13 only, the one version with neither of the above).
 */
class GunItems(private val plugin: ArmedPlugin) {
    companion object {
        const val SHOP_ID = "_shop"
        private const val CC = '§'
        private val MARK = "${CC}r${CC}0${CC}a${CC}r"
        private const val HEX = "0123456789abcdef"
    }

    private class Pdc(plugin: ArmedPlugin) {
        private val key: Any
        private val type: Any
        private val getContainer: Method
        private val setM: Method
        private val getM: Method

        init {
            val keyClass = Class.forName("org.bukkit.NamespacedKey")
            key = keyClass.getConstructor(org.bukkit.plugin.Plugin::class.java, String::class.java).newInstance(plugin, "armed_id")
            val typeClass = Class.forName("org.bukkit.persistence.PersistentDataType")
            type = typeClass.getField("STRING").get(null)
            getContainer = Class.forName("org.bukkit.persistence.PersistentDataHolder").getMethod("getPersistentDataContainer")
            val containerClass = Class.forName("org.bukkit.persistence.PersistentDataContainer")
            setM = containerClass.getMethod("set", keyClass, typeClass, Any::class.java)
            getM = containerClass.getMethod("get", keyClass, typeClass)
        }

        fun set(meta: ItemMeta, value: String) {
            setM.invoke(getContainer.invoke(meta), key, type, value)
        }

        fun get(meta: ItemMeta): String? = getM.invoke(getContainer.invoke(meta), key, type) as String?
    }

    private val pdc: Pdc? = if (Version.hasPdc) {
        try {
            Pdc(plugin)
        } catch (t: Throwable) {
            plugin.logger.warning("PersistentDataContainer unavailable, falling back to name matching: $t")
            null
        }
    } else null

    private val useMarker = !Version.isFlattened

    // ---------------------------------------------------------------- identification

    fun isEmpty(item: ItemStack?): Boolean = item == null || item.type.name.endsWith("AIR")

    /** Returns the gun id or [SHOP_ID] stored on [item], or null for any other item. */
    fun identify(item: ItemStack?): String? {
        if (isEmpty(item) || !item!!.hasItemMeta()) return null
        val meta = item.itemMeta ?: return null
        try {
            pdc?.get(meta)?.let { return it }
        } catch (_: Throwable) {
        }
        if (!meta.hasDisplayName()) return null
        val name = meta.displayName
        if (useMarker) decodeMarker(name)?.let { return it }
        if (!Version.hasPdc) return matchByName(item, name)
        return null
    }

    fun gunOf(item: ItemStack?): GunDefinition? {
        val id = identify(item) ?: return null
        return if (id == SHOP_ID) null else plugin.registry.get(id)
    }

    fun isShopItem(item: ItemStack?): Boolean = identify(item) == SHOP_ID

    private fun encodeMarker(id: String): String {
        val sb = StringBuilder(MARK)
        for (b in id.toByteArray(Charsets.UTF_8)) {
            val v = b.toInt() and 0xFF
            sb.append(CC).append(HEX[v shr 4]).append(CC).append(HEX[v and 0xF])
        }
        return sb.toString()
    }

    private fun decodeMarker(name: String): String? {
        val idx = name.lastIndexOf(MARK)
        if (idx < 0) return null
        val rest = name.substring(idx + MARK.length)
        if (rest.isEmpty() || rest.length % 4 != 0) return null
        val bytes = ByteArray(rest.length / 4)
        var i = 0
        while (i < rest.length) {
            if (rest[i] != CC || rest[i + 2] != CC) return null
            val hi = HEX.indexOf(rest[i + 1])
            val lo = HEX.indexOf(rest[i + 3])
            if (hi < 0 || lo < 0) return null
            bytes[i / 4] = ((hi shl 4) or lo).toByte()
            i += 4
        }
        return String(bytes, Charsets.UTF_8)
    }

    private fun matchByName(item: ItemStack, name: String): String? {
        val plain = Text.strip(name)
        val shopName = Text.strip(plugin.configs.config.getString("shop-item.name"))
        if (shopName.isNotEmpty() && (plain == shopName || plain.startsWith("$shopName "))) {
            val shopMat = Mats.resolve(plugin.configs.config.getString("shop-item.material"))
            if (shopMat == null || shopMat == item.type) return SHOP_ID
        }
        var best: GunDefinition? = null
        var bestLen = -1
        for (g in plugin.registry.all()) {
            val n = Text.strip(g.displayName)
            if (n.isEmpty()) continue
            if (plain == n || plain.startsWith("$n ")) {
                if (Mats.resolve(g.material.substringBefore(':')) != item.type) continue
                if (n.length > bestLen) {
                    best = g
                    bestLen = n.length
                }
            }
        }
        return best?.id
    }

    // ---------------------------------------------------------------- creation

    fun displayName(g: GunDefinition, s: GunState?): String {
        val cfg = plugin.configs.config
        val template = when {
            s != null && s.reloading -> cfg.getString("item.reloading-name-format") ?: "{name} &c[Reloading]"
            g.reserveLimited -> cfg.getString("item.name-format-reserve") ?: "{name} &8[&e{ammo}&7/&e{clip} &8| &7{reserve}&8]"
            else -> cfg.getString("item.name-format") ?: "{name} &8[&e{ammo}&7/&e{clip}&8]"
        }
        val ammo = s?.ammo ?: g.clipSize
        val reserve = if (g.reserveLimited) (s?.reserve ?: g.reserveAmmo).toString() else "∞"
        return Text.color(Text.fmt(template, "name" to g.displayName, "ammo" to ammo, "clip" to g.clipSize, "reserve" to reserve))
    }

    @Suppress("DEPRECATION")
    private fun hideFlags(meta: ItemMeta) {
        try {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        } catch (_: Throwable) {
        }
    }

    fun createGun(g: GunDefinition, s: GunState?): ItemStack {
        val stack = Mats.stack(g.material)
        val meta = stack.itemMeta ?: return stack
        var name = displayName(g, s)
        if (useMarker) name += encodeMarker(g.id)
        meta.displayName = name
        meta.lore = GunText.lines(plugin, plugin.configs.config.getStringList("item.lore"), g)
        hideFlags(meta)
        try {
            pdc?.set(meta, g.id)
        } catch (_: Throwable) {
        }
        stack.itemMeta = meta
        return stack
    }

    fun createShopItem(): ItemStack {
        val cfg = plugin.configs.config
        val stack = Mats.stack(cfg.getString("shop-item.material"), 1, "NETHER_STAR")
        val meta = stack.itemMeta ?: return stack
        var name = Text.color(cfg.getString("shop-item.name") ?: "&6Gun Shop")
        if (useMarker) name += encodeMarker(SHOP_ID)
        meta.displayName = name
        meta.lore = cfg.getStringList("shop-item.lore").map { Text.color(it) }
        hideFlags(meta)
        try {
            pdc?.set(meta, SHOP_ID)
        } catch (_: Throwable) {
        }
        stack.itemMeta = meta
        return stack
    }

    // ---------------------------------------------------------------- updates

    /** Rewrites only the display name of the gun in [slot] (ammo counter / reloading tag). */
    fun updateName(player: Player, slot: Int, g: GunDefinition, s: GunState) {
        val item = player.inventory.getItem(slot) ?: return
        val meta = item.itemMeta ?: return
        var name = displayName(g, s)
        if (useMarker) name += encodeMarker(g.id)
        meta.displayName = name
        item.itemMeta = meta
        player.inventory.setItem(slot, item)
    }

    /** Rebuilds every gun / shop item of every online player after a config change. Ammo is kept. */
    fun refreshAll() {
        for (player in plugin.server.onlinePlayers) {
            val inv = player.inventory
            var changed = false
            for (slot in 0 until inv.size) {
                val item = inv.getItem(slot) ?: continue
                val id = identify(item) ?: continue
                if (id == SHOP_ID) {
                    inv.setItem(slot, createShopItem())
                    changed = true
                    continue
                }
                val gun = plugin.registry.get(id) ?: continue
                val state = plugin.states.get(player, gun)
                val fresh = createGun(gun, state)
                fresh.amount = item.amount
                inv.setItem(slot, fresh)
                changed = true
            }
            if (changed) Currencies.refresh(player)
        }
    }
}
