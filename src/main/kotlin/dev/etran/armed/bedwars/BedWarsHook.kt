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

    /** Names of all arena groups currently known to BedWars1058 (for tab completion). */
    fun groups(): Set<String> =
        try {
            api?.arenaUtil?.arenas?.mapNotNull { it.group }?.toSet() ?: emptySet()
        } catch (_: Throwable) {
            emptySet()
        }

    /** Names of the maps (arenas) in [group], for tab completion. */
    fun maps(group: String): List<String> =
        try {
            api?.arenaUtil?.arenas?.filter { it.group.equals(group, true) }?.map { it.arenaName } ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }

    fun isPlaying(arena: IArena): Boolean = arena.status == GameState.playing

    /**
     * Whether [player] has a BedWars1058 item shop page open (main page or a category), asked through BedWars1058's
     * viewer lists. Null when those lists cannot be reached, for example on a build with a different layout.
     */
    fun isShopViewer(player: Player): Boolean? =
        try {
            val index = viewers("com.andrei1058.bedwars.shop.main.ShopIndex", "getIndexViewers")
            val category = viewers("com.andrei1058.bedwars.shop.main.ShopCategory", "getCategoryViewers")
            index.contains(player.uniqueId) || category.contains(player.uniqueId)
        } catch (_: Throwable) {
            null
        }

    private fun viewers(className: String, method: String): Collection<*> =
        Class.forName(className).getMethod(method).invoke(null) as Collection<*>

    /** True when [a] and [b] are on the same BedWars team. */
    fun sameTeam(arena: IArena, a: Player, b: Player): Boolean {
        val ta = arena.getTeam(a) ?: return false
        val tb = arena.getTeam(b) ?: return false
        return ta === tb
    }
}
