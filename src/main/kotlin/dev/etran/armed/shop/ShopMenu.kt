package dev.etran.armed.shop

import dev.etran.armed.ArmedPlugin
import dev.etran.armed.gun.GunDefinition
import dev.etran.armed.gun.GunText
import dev.etran.armed.util.Mats
import dev.etran.armed.util.Sounds
import dev.etran.armed.util.Text
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack

/** Marks an inventory as the gun shop and remembers which slot sells which gun. */
class ShopHolder : InventoryHolder {
    val gunBySlot = HashMap<Int, String>()
    var closeSlot = -1
    private lateinit var inv: Inventory

    fun bind(inventory: Inventory) {
        inv = inventory
    }

    override fun getInventory(): Inventory = inv
}

class ShopMenu(private val plugin: ArmedPlugin) {

    fun open(player: Player) {
        if (!player.hasPermission("armed.shop")) {
            plugin.messages.send(player, "no-permission")
            return
        }
        if (!plugin.controller.canUse(player)) {
            plugin.messages.send(player, "not-in-game")
            Sounds.play(player, plugin.settings.errorSound)
            return
        }
        val guns = plugin.registry.all().filter { it.shopEnabled }
        if (guns.isEmpty()) {
            plugin.messages.send(player, "shop-no-guns")
            return
        }
        val cfg = plugin.configs.config
        val layout = layout(guns, cfg.getInt("shop.rows", 3).coerceIn(1, 6))
        val holder = ShopHolder()
        val inv = Bukkit.createInventory(holder, layout.rows * 9, Text.color(cfg.getString("shop.title") ?: "Gun Shop").take(32))
        holder.bind(inv)
        holder.gunBySlot.putAll(layout.slots)
        holder.closeSlot = layout.closeSlot
        render(player, holder)
        player.openInventory(inv)
    }

    private class Layout(val rows: Int, val slots: Map<Int, String>, val closeSlot: Int)

    private fun layout(guns: List<GunDefinition>, configuredRows: Int): Layout {
        var rows = configuredRows
        val needed = guns.filter { it.shopSlot >= 0 }.maxOfOrNull { it.shopSlot / 9 + 1 } ?: 1
        rows = Math.max(rows, Math.min(needed, 6))

        // auto slots: the inner area when there is a border, otherwise everything
        val auto = ArrayList<Int>()
        if (rows >= 3) {
            for (r in 1..rows - 2) for (c in 1..7) auto += r * 9 + c
        } else {
            for (i in 0 until rows * 9) auto += i
        }

        val taken = HashMap<Int, String>()
        for (g in guns) {
            if (g.shopSlot in 0 until rows * 9 && !taken.containsKey(g.shopSlot)) taken[g.shopSlot] = g.id
        }
        for (g in guns) {
            if (taken.containsValue(g.id)) continue
            val free = auto.firstOrNull { !taken.containsKey(it) } ?: (0 until rows * 9).firstOrNull { !taken.containsKey(it) }
            if (free == null) {
                plugin.logger.warning("Shop is full, gun '${g.id}' does not fit. Increase shop.rows.")
                continue
            }
            taken[free] = g.id
        }

        val cfg = plugin.configs.config
        var close = -1
        if (cfg.getBoolean("shop.close-button.enabled", true)) {
            val want = cfg.getInt("shop.close-button.slot", rows * 9 - 5)
            close = if (want in 0 until rows * 9 && !taken.containsKey(want)) want else (rows * 9 - 5).takeIf { !taken.containsKey(it) } ?: -1
        }
        return Layout(rows, taken, close)
    }

    /** (Re)draws the whole menu for [player]; used on open and after every purchase. */
    fun render(player: Player, holder: ShopHolder) {
        val inv = holder.inventory
        val cfg = plugin.configs.config
        inv.clear()

        if (cfg.getBoolean("shop.filler.enabled", true)) {
            val filler = Mats.stack(cfg.getString("shop.filler.material"), 1, "GRAY_STAINED_GLASS_PANE")
            named(filler, cfg.getString("shop.filler.name") ?: " ", emptyList())
            for (i in 0 until inv.size) inv.setItem(i, filler.clone())
        }
        for ((slot, id) in holder.gunBySlot) {
            val gun = plugin.registry.get(id) ?: continue
            inv.setItem(slot, shopItem(player, gun))
        }
        if (holder.closeSlot >= 0) {
            val close = Mats.stack(cfg.getString("shop.close-button.material"), 1, "BARRIER")
            named(close, cfg.getString("shop.close-button.name") ?: "&cClose", emptyList())
            inv.setItem(holder.closeSlot, close)
        }
    }

    private fun named(stack: ItemStack, name: String, lore: List<String>) {
        val meta = stack.itemMeta ?: return
        meta.displayName = Text.color(name)
        if (lore.isNotEmpty()) meta.lore = lore
        try {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        } catch (_: Throwable) {
        }
        stack.itemMeta = meta
    }

    private enum class Status { OWNED, REFILL, BUY, POOR, DENIED }

    private fun status(player: Player, gun: GunDefinition): Status {
        if (gun.permission.isNotEmpty() && !player.hasPermission(gun.permission)) return Status.DENIED
        if (plugin.controller.owns(player, gun)) return if (gun.reserveLimited) Status.REFILL else Status.OWNED
        return if (canAfford(player, gun)) Status.BUY else Status.POOR
    }

    private fun canAfford(player: Player, gun: GunDefinition): Boolean {
        if (gun.free) return true
        val c = plugin.currencies.get(gun.costCurrency) ?: return true // unknown currency => free (warned on load)
        return plugin.currencies.count(player, c) >= gun.costAmount
    }

    private fun shopItem(player: Player, gun: GunDefinition): ItemStack {
        val stack = Mats.stack(gun.material)
        val statusKey = when (status(player, gun)) {
            Status.OWNED -> "shop-status-owned"
            Status.REFILL -> "shop-status-refill"
            Status.BUY -> "shop-status-buy"
            Status.POOR -> "shop-status-poor"
            Status.DENIED -> "shop-status-no-permission"
        }
        val cost = plugin.currencies.costText(gun, plugin.messages.get("shop-cost-free"))
        val statusText = plugin.messages.get(statusKey, "cost" to Text.color(cost))
        val lore = GunText.lines(plugin, plugin.configs.config.getStringList("shop.lore"), gun, "status" to statusText)
        named(stack, gun.displayName, lore)
        return stack
    }

    // ------------------------------------------------------------------ purchase

    fun click(player: Player, holder: ShopHolder, slot: Int) {
        if (slot == holder.closeSlot) {
            player.closeInventory()
            return
        }
        val gun = plugin.registry.get(holder.gunBySlot[slot]) ?: return
        buy(player, gun)
        if (plugin.configs.config.getBoolean("shop.close-on-buy", false)) player.closeInventory()
        else render(player, holder)
    }

    fun buy(player: Player, gun: GunDefinition) {
        val m = plugin.messages
        val s = plugin.settings
        val name = Text.color(gun.displayName)
        fun fail(key: String) {
            m.send(player, key, "name" to name, "cost" to Text.color(plugin.currencies.costText(gun, m.get("shop-cost-free"))))
            Sounds.play(player, s.errorSound)
        }

        if (!plugin.controller.canUse(player)) return fail("not-in-game")
        if (gun.permission.isNotEmpty() && !player.hasPermission(gun.permission)) return fail("shop-status-no-permission")

        val owned = plugin.controller.owns(player, gun)
        if (owned && !gun.reserveLimited) return fail("already-owned")
        if (!canAfford(player, gun)) return fail("cannot-afford")

        val currency = if (gun.free) null else plugin.currencies.get(gun.costCurrency)
        if (currency != null) plugin.currencies.take(player, currency, gun.costAmount)

        if (owned) {
            // limited reserve: paying again refills the gun
            val state = plugin.states.get(player, gun)
            state.ammo = gun.clipSize
            state.reserve = gun.reserveAmmo
            val inv = player.inventory
            for (i in 0 until inv.size) {
                if (plugin.items.identify(inv.getItem(i)) == gun.id) plugin.items.updateName(player, i, gun, state)
            }
            m.send(player, "refilled", "name" to name)
        } else {
            if (!plugin.controller.giveGun(player, gun)) {
                if (currency != null) plugin.currencies.give(player, currency, gun.costAmount)
                return fail("inventory-full")
            }
            m.send(player, "bought", "name" to name)
        }
        Sounds.play(player, s.purchaseSound)
    }
}
