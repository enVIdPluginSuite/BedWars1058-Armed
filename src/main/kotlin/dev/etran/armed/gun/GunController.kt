package dev.etran.armed.gun

import dev.etran.armed.ArmedPlugin
import dev.etran.armed.bedwars.BedWarsHook
import dev.etran.armed.util.ActionBar
import dev.etran.armed.util.Sounds
import dev.etran.armed.util.Text
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable

/** Everything that happens when a player pulls the trigger, reloads, buys or receives a gun. */
class GunController(private val plugin: ArmedPlugin) {

    // ------------------------------------------------------------------ rules

    /** Whether [player] may use guns / the shop right now. */
    fun canUse(player: Player): Boolean {
        val arena = BedWarsHook.arenaOf(player)
        if (arena != null) return BedWarsHook.isPlaying(arena) && !arena.isSpectator(player)
        return !plugin.settings.requireArena
    }

    /** The gun the player holds in the main hand, with its slot. */
    fun held(player: Player): Pair<Int, GunDefinition>? {
        val inv = player.inventory
        val slot = inv.heldItemSlot
        val gun = plugin.items.gunOf(inv.getItem(slot)) ?: return null
        return slot to gun
    }

    // ------------------------------------------------------------------ shooting

    fun trigger(player: Player) {
        val (slot, gun) = held(player) ?: return
        if (!canUse(player)) return
        val state = plugin.states.get(player, gun)
        if (state.reloading) return

        val now = System.currentTimeMillis()
        val cooldown = gun.triggerCooldownMs - plugin.settings.fireRateToleranceMs
        if (now - state.lastTrigger < cooldown) return

        if (state.ammo <= 0) {
            state.lastTrigger = now
            Sounds.play(player, gun.soundEmpty)
            if (plugin.settings.autoReload) startReload(player, slot, gun, state)
            announce(player, gun, state)
            return
        }

        state.lastTrigger = now
        shootOnce(player, slot, gun, state)

        for (i in 1 until gun.burstSize) {
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (!player.isOnline || player.isDead) return@Runnable
                val cur = held(player)
                if (cur == null || cur.second.id != gun.id || cur.first != slot) return@Runnable
                if (state.reloading || state.ammo <= 0 || !canUse(player)) return@Runnable
                shootOnce(player, slot, gun, state)
            }, gun.burstIntervalTicks * i)
        }
    }

    private fun shootOnce(player: Player, slot: Int, gun: GunDefinition, state: GunState) {
        state.ammo -= 1
        plugin.ballistics.fire(player, gun)
        plugin.items.updateName(player, slot, gun, state)
        announce(player, gun, state)
        if (state.ammo <= 0 && plugin.settings.autoReload && (state.reserve != 0)) {
            startReload(player, slot, gun, state)
        }
    }

    // ------------------------------------------------------------------ reloading

    /** Manual reload of the gun in the main hand. */
    fun reload(player: Player) {
        val (slot, gun) = held(player) ?: return
        if (!canUse(player)) return
        val state = plugin.states.get(player, gun)
        if (state.reloading || state.ammo >= gun.clipSize) return
        startReload(player, slot, gun, state)
    }

    private fun startReload(player: Player, slot: Int, gun: GunDefinition, state: GunState) {
        if (state.reloading || state.ammo >= gun.clipSize) return
        if (state.reserve == 0) {
            announce(player, gun, state)
            return
        }
        state.reloading = true
        val total = gun.reloadMs
        val start = System.currentTimeMillis()
        val xp = plugin.settings.reloadXpBar
        if (xp) {
            state.savedExp = player.exp
            state.savedLevel = player.level
        }
        Sounds.playAround(player.location, gun.soundReloadStart, plugin.settings.soundRadius)
        plugin.items.updateName(player, slot, gun, state)
        announce(player, gun, state)

        if (total <= 0L) {
            finishReload(player, slot, gun, state)
            return
        }

        state.reloadTask = object : BukkitRunnable() {
            override fun run() {
                if (!player.isOnline || !state.reloading) {
                    cancel()
                    return
                }
                val stillThere = plugin.items.gunOf(player.inventory.getItem(slot))?.id == gun.id
                if (!stillThere) {
                    abortReload(player, state)
                    return
                }
                val elapsed = System.currentTimeMillis() - start
                if (elapsed >= total) {
                    cancel()
                    finishReload(player, slot, gun, state)
                    return
                }
                if (xp) {
                    player.exp = (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 0.999f)
                    player.level = Math.ceil((total - elapsed) / 1000.0).toInt()
                }
            }
        }.runTaskTimer(plugin, 2L, 2L)
    }

    private fun finishReload(player: Player, slot: Int, gun: GunDefinition, state: GunState) {
        state.reloadTask?.cancel()
        state.reloadTask = null
        state.reloading = false
        val needed = gun.clipSize - state.ammo
        if (state.reserve < 0) {
            state.ammo = gun.clipSize
        } else {
            val moved = Math.min(needed, state.reserve)
            state.ammo += moved
            state.reserve -= moved
        }
        if (plugin.settings.reloadXpBar) {
            player.exp = state.savedExp
            player.level = state.savedLevel
        }
        Sounds.playAround(player.location, gun.soundReloadEnd, plugin.settings.soundRadius)
        plugin.items.updateName(player, slot, gun, state)
        announce(player, gun, state)
    }

    private fun abortReload(player: Player, state: GunState) {
        state.reloadTask?.cancel()
        state.reloadTask = null
        state.reloading = false
        if (plugin.settings.reloadXpBar) {
            player.exp = state.savedExp
            player.level = state.savedLevel
        }
    }

    /** Called when the player scrolls away from [slot]. */
    fun onSwitchAway(player: Player, slot: Int) {
        if (!plugin.settings.cancelReloadOnSwitch) return
        val gun = plugin.items.gunOf(player.inventory.getItem(slot)) ?: return
        val state = plugin.states.get(player, gun)
        if (!state.reloading) return
        abortReload(player, state)
        plugin.items.updateName(player, slot, gun, state)
    }

    // ------------------------------------------------------------------ feedback

    fun announce(player: Player, gun: GunDefinition, state: GunState) {
        if (!plugin.settings.actionBar) return
        val key = when {
            state.reloading -> "action-bar-reloading"
            state.ammo <= 0 && state.reserve == 0 -> "action-bar-empty"
            else -> "action-bar"
        }
        val reserveSuffix = if (gun.reserveLimited) plugin.messages.get("reserve-suffix", "reserve" to state.reserve) else ""
        val text = plugin.messages.get(
            key,
            "name" to Text.color(gun.displayName),
            "ammo" to state.ammo,
            "clip" to gun.clipSize,
            "reserve" to state.reserve,
            "reserve_suffix" to reserveSuffix
        )
        if (text.isNotEmpty()) ActionBar.send(player, text)
    }

    // ------------------------------------------------------------------ inventory

    fun owns(player: Player, gun: GunDefinition): Boolean {
        val inv = player.inventory
        for (i in 0 until inv.size) {
            if (plugin.items.identify(inv.getItem(i)) == gun.id) return true
        }
        return false
    }

    fun removeGun(player: Player, gunId: String) {
        val inv = player.inventory
        for (i in 0 until inv.size) {
            if (plugin.items.identify(inv.getItem(i)) == gunId) inv.setItem(i, null)
        }
    }

    /** Puts [item] in [preferred] if that hotbar slot is free, else the first free hotbar slot, else anywhere. */
    fun place(player: Player, item: ItemStack, preferred: Int): Boolean {
        val inv = player.inventory
        var slot = -1
        if (preferred in 0..8 && plugin.items.isEmpty(inv.getItem(preferred))) slot = preferred
        if (slot < 0) {
            for (i in 0..8) {
                if (plugin.items.isEmpty(inv.getItem(i))) {
                    slot = i
                    break
                }
            }
        }
        if (slot < 0) slot = inv.firstEmpty()
        if (slot < 0) return false
        inv.setItem(slot, item)
        Currencies.refresh(player)
        return true
    }

    /** Gives a fresh, fully loaded copy of [gun], replacing any copy the player already has. */
    fun giveGun(player: Player, gun: GunDefinition): Boolean {
        removeGun(player, gun.id)
        val state = plugin.states.reset(player, gun)
        val ok = place(player, plugin.items.createGun(gun, state), gun.hotbarSlot)
        if (!ok) plugin.states.clear(player)
        return ok
    }

    fun giveShopItem(player: Player) {
        val cfg = plugin.configs.config
        if (!cfg.getBoolean("shop-item.enabled", true)) return
        val inv = player.inventory
        for (i in 0 until inv.size) {
            if (plugin.items.isShopItem(inv.getItem(i))) return
        }
        place(player, plugin.items.createShopItem(), cfg.getInt("shop-item.slot", 8))
    }

    /** Starter guns + shop item, called after (re)spawn. */
    fun giveLoadout(player: Player, respawn: Boolean) {
        for (gun in plugin.registry.all()) {
            if (gun.starter && !owns(player, gun)) giveGun(player, gun)
        }
        val key = if (respawn) "shop-item.give-on-respawn" else "shop-item.give-on-spawn"
        if (plugin.configs.config.getBoolean(key, true)) giveShopItem(player)
    }
}
