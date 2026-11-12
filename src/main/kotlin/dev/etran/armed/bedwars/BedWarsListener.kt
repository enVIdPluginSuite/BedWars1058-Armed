package dev.etran.armed.bedwars

import com.andrei1058.bedwars.api.events.player.PlayerFirstSpawnEvent
import com.andrei1058.bedwars.api.events.player.PlayerLeaveArenaEvent
import com.andrei1058.bedwars.api.events.player.PlayerReSpawnEvent
import dev.etran.armed.ArmedPlugin
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class BedWarsListener(private val plugin: dev.etran.armed.ArmedPlugin) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onFirstSpawn(e: PlayerFirstSpawnEvent) = loadout(e.player)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onRespawn(e: PlayerReSpawnEvent) = loadout(e.player)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onLeave(e: PlayerLeaveArenaEvent) {
        plugin.states.clear(e.player, false)
    }

    private fun loadout(player: Player) {
        // BedWars1058 fills the inventory right around these events, so wait one tick.
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (player.isOnline && plugin.controller.canUse(player)) {
                plugin.states.clear(player, false)
                plugin.controller.giveLoadout(player)
            }
        }, 2L)
    }
}
