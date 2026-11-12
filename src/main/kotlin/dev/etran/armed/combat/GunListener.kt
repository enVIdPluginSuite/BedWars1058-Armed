package dev.etran.armed.combat

import dev.etran.armed.ArmedPlugin
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID

class GunListener(private val plugin: ArmedPlugin) : Listener {
    // On 1.9+ every click fires once per hand. Both fire within the same tick, so anything closer than
    // this to the previous handled click is the off-hand duplicate.
    private val lastClick = HashMap<UUID, Long>()
    private val duplicateWindowNanos = 25_000_000L

    @EventHandler(priority = EventPriority.HIGH)
    fun onInteract(e: PlayerInteractEvent) {
        val action = e.action
        if (action == Action.PHYSICAL) return
        val player = e.player
        val inv = player.inventory
        plugin.items.identify(inv.getItem(inv.heldItemSlot)) ?: return

        val right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK
        val now = System.nanoTime()
        val prev = lastClick[player.uniqueId]
        if (prev != null && now - prev < duplicateWindowNanos) {
            if (right && plugin.settings.cancelGunInteractions) e.isCancelled = true
            return
        }
        lastClick[player.uniqueId] = now

        if (right) {
            if (plugin.settings.cancelGunInteractions) e.isCancelled = true
            plugin.controller.trigger(player)
        } else if (plugin.settings.reloadOnLeftClick) {
            plugin.controller.reload(player)
        }
    }

    /** A left click that hits a player is not an interact event, so punching with a gun reloads it as well. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPunch(e: EntityDamageByEntityEvent) {
        if (plugin.ballistics.firing) return // our own bullets are attack events too
        if (!plugin.settings.reloadOnLeftClick || e.cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return
        val player = e.damager as? org.bukkit.entity.Player ?: return
        plugin.controller.reload(player)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onDrop(e: PlayerDropItemEvent) {
        val id = plugin.items.identify(e.itemDrop.itemStack) ?: return
        e.isCancelled = true
        if (plugin.settings.reloadOnDrop) {
            val held = plugin.controller.held(e.player)
            if (held != null && held.second.id == id) plugin.controller.reload(e.player)
        }
    }

    @EventHandler
    fun onHeld(e: PlayerItemHeldEvent) {
        val player = e.player
        plugin.controller.onSwitchAway(player, e.previousSlot)
        val next = plugin.items.gunOf(player.inventory.getItem(e.newSlot)) ?: return
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (player.isOnline) plugin.controller.announce(player, next, plugin.states.get(player, next))
        })
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDeath(e: PlayerDeathEvent) {
        plugin.states.clear(e.entity, false)
    }

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        plugin.states.clear(e.player)
        lastClick.remove(e.player.uniqueId)
    }
}
