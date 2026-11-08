package dev.etran.armed.bedwars

import com.andrei1058.bedwars.api.BedWars
import com.andrei1058.bedwars.api.arena.GameState
import com.andrei1058.bedwars.api.arena.IArena
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.logging.Logger

/** Thin wrapper around the BedWars1058 API. Everything else in the plugin goes through here. */
object BedWarsHook {
    private var api: BedWars? = null

    fun init(log: Logger): Boolean {
        val reg = Bukkit.getServicesManager().getRegistration(BedWars::class.java)
        api = reg?.provider
        if (api == null) log.severe("BedWars1058 did not register its API service.")
        return api != null
    }

    fun arenaOf(player: Player): IArena? = api?.arenaUtil?.getArenaByPlayer(player)

    fun isPlaying(arena: IArena): Boolean = arena.status == GameState.playing

    /** True when [a] and [b] are on the same BedWars team. */
    fun sameTeam(arena: IArena, a: Player, b: Player): Boolean {
        val ta = arena.getTeam(a) ?: return false
        val tb = arena.getTeam(b) ?: return false
        return ta === tb
    }
}
