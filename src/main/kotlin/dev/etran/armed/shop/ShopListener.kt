package dev.etran.armed.shop

import dev.etran.armed.ArmedPlugin
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent

/**
 * Note: InventoryView became an interface in 1.21, so calling event.getView() from a jar compiled
 * against 1.8 throws IncompatibleClassChangeError there. Only event.inventory (the top inventory) is used.
 */
class ShopListener(private val plugin: ArmedPlugin) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onClick(e: InventoryClickEvent) {
        val holder = e.inventory.holder as? ShopHolder ?: return
        e.isCancelled = true
        val player = e.whoClicked as? Player ?: return
        val raw = e.rawSlot
        if (raw < 0 || raw >= e.inventory.size) return // clicks in the player's own inventory do nothing
        plugin.shop.click(player, holder, raw)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onDrag(e: InventoryDragEvent) {
        if (e.inventory.holder is ShopHolder) e.isCancelled = true
    }
}
