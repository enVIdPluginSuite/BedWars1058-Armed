package dev.etran.armed.bedwars

import dev.etran.armed.ArmedPlugin
import dev.etran.armed.util.Mats
import dev.etran.armed.util.Text
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag
import java.util.UUID

/**
 * Adds a "Guns" button to BedWars1058's own item shop (the main page and every category page), which opens the
 * Armed gun menu. The shop is a plain inventory without a holder, so it is recognised by asking BedWars1058 who is
 * currently viewing its shop; BedWars1058 registers the viewer right after opening the inventory, so the button is
 * added one tick later.
 *
 * Only event.inventory (the top inventory) is used here: InventoryView changed from a class to an interface in
 * 1.21, so event.getView() would fail on newer servers with a jar compiled against 1.8.
 */
class ShopButtonListener(private val plugin: ArmedPlugin) : Listener {
    private class Button(val inventory: Inventory, val slot: Int)

    private val buttons = HashMap<UUID, Button>()

    @EventHandler(priority = EventPriority.MONITOR)
    fun onOpen(e: InventoryOpenEvent) {
        val player = e.player as? Player ?: return
        val inv = e.inventory
        if (inv.holder != null || inv.type != InventoryType.CHEST) return
        if (!plugin.configs.config.getBoolean("shop-button.enabled", true)) return
        plugin.server.scheduler.runTask(plugin, Runnable { addButton(player, inv) })
    }

    private fun addButton(player: Player, inv: Inventory) {
        if (!player.isOnline || !plugin.controller.canUse(player)) return
        val viewing = BedWarsHook.isShopViewer(player) ?: looksLikeShop(inv)
        if (!viewing) return

        val cfg = plugin.configs.config
        var slot = cfg.getInt("shop-button.slot", -1)
        if (slot < 0 || slot >= inv.size) {
            slot = (0..8).firstOrNull { it < inv.size && plugin.items.isEmpty(inv.getItem(it)) } ?: (inv.size - 1)
        }
        if (!plugin.items.isEmpty(inv.getItem(slot))) return // never replace something of the shop

        val stack = Mats.stack(cfg.getString("shop-button.material"), 1, "DIAMOND_HOE")
        val meta = stack.itemMeta
        if (meta != null) {
            meta.displayName = Text.color(cfg.getString("shop-button.name") ?: "&6&lArmed Guns")
            meta.lore = cfg.getStringList("shop-button.lore").map { Text.color(it) }
            try {
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
            } catch (_: Throwable) {
            }
            stack.itemMeta = meta
        }
        inv.setItem(slot, stack)
        buttons[player.uniqueId] = Button(inv, slot)
    }

    /** Fallback when BedWars1058's internals cannot be asked: its shop has a separator row in slots 9-17. */
    private fun looksLikeShop(inv: Inventory): Boolean {
        if (inv.size < 27) return false
        return (9..17).all { !plugin.items.isEmpty(inv.getItem(it)) }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onClick(e: InventoryClickEvent) {
        val player = e.whoClicked as? Player ?: return
        val button = buttons[player.uniqueId] ?: return
        if (e.inventory !== button.inventory) return
        if (e.rawSlot != button.slot) return
        e.isCancelled = true
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (player.isOnline) plugin.shop.open(player)
        })
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onClose(e: InventoryCloseEvent) {
        val id = e.player.uniqueId
        if (buttons[id]?.inventory === e.inventory) buttons.remove(id)
    }

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        buttons.remove(e.player.uniqueId)
    }
}
