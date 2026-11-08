package dev.etran.armed.gun

import dev.etran.armed.ArmedPlugin
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

/** Mutable runtime state of one gun owned by one player. Ammo lives here, not on the item. */
class GunState(var ammo: Int, var reserve: Int) {
    var lastTrigger = 0L
    var reloading = false
    var reloadTask: BukkitTask? = null
    var savedExp = 0f
    var savedLevel = 0
}

class GunStates(private val plugin: ArmedPlugin) {
    private val map = HashMap<UUID, HashMap<String, GunState>>()

    fun get(player: Player, gun: GunDefinition): GunState {
        val perPlayer = map.getOrPut(player.uniqueId) { HashMap() }
        val state = perPlayer.getOrPut(gun.id) { fresh(gun) }
        if (state.ammo > gun.clipSize) state.ammo = gun.clipSize
        return state
    }

    /** Gives the player a brand new full gun state (used when a gun is bought or granted). */
    fun reset(player: Player, gun: GunDefinition): GunState {
        val perPlayer = map.getOrPut(player.uniqueId) { HashMap() }
        perPlayer[gun.id]?.reloadTask?.cancel()
        val s = fresh(gun)
        perPlayer[gun.id] = s
        return s
    }

    private fun fresh(gun: GunDefinition) = GunState(gun.clipSize, if (gun.reserveLimited) gun.reserveAmmo else -1)

    /** Cancels reloads and forgets all state of [player]. Restores the xp bar if a reload was running. */
    fun clear(player: Player, restoreXp: Boolean = true) {
        val perPlayer = map.remove(player.uniqueId) ?: return
        for (state in perPlayer.values) {
            state.reloadTask?.cancel()
            if (state.reloading && restoreXp && player.isOnline) {
                player.exp = state.savedExp
                player.level = state.savedLevel
            }
            state.reloading = false
        }
    }

    fun clearAll() {
        for (p in plugin.server.onlinePlayers) clear(p)
        map.clear()
    }
}
