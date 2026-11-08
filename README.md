# BedWars1058-Armed

Hypixel's *Armed* Bedwars dream mode for [BedWars1058](https://github.com/andrei1058/BedWars1058): hitscan guns, a separate gun shop, and everything (guns, shop, messages, rules) editable in game.

* Kotlin 2.2.21, Gradle (Kotlin DSL), Java 8 bytecode, one jar for Minecraft 1.8 - 26.x
* Hard-depends on BedWars1058; guns only work for living players of a running arena (switchable for testing)

## Build

```
./gradlew build
```

The jar is `build/libs/BedWars1058-Armed-<version>.jar` (Kotlin is bundled and relocated).
If your network cannot reach `repo.andrei1058.com`, build against a jar instead:
`./gradlew build -PbedwarsApiJar=/path/to/bedwars-api.jar`. The API version is `bedwarsApiVersion` in `gradle.properties`.

## Playing

* Everybody gets the **Pistol** (free) and a **Gun Shop** star at spawn / respawn.
* Right-click the star or run `/armed shop` to open the shop. Guns are paid for with the resources in your inventory.
* **Right-click** shoots (hold for automatic fire), **drop (Q)** reloads, empty guns auto-reload.
* Guns are lost on death like any other item; starter guns come back on respawn.

## Commands (`/armed`, aliases `/guns`, `/gunshop`)

| Command | Permission |
|---|---|
| `/armed shop` | `armed.shop` (default true) |
| `/armed list`, `/armed info <gun>` | `armed.use` |
| `/armed give <player> <gun>` | `armed.admin.give` |
| `/armed reload` | `armed.admin.reload` |
| `/armed set <gun> <property> <value>` | `armed.admin.edit` |
| `/armed create <id> [copyFrom]`, `/armed delete <id>` | `armed.admin.edit` |
| `/armed description <gun> add <text> \| remove <n> \| clear` | `armed.admin.edit` |
| `/armed config <path> [value]` (any single value in config.yml) | `armed.admin.edit` |

`armed.admin` grants all of the above (default: op). Every edit is saved to disk and applied immediately, including to guns players are already holding (ammo is kept). Properties tab-complete.

### Adding a gun in game

```
/armed create laser pistol        # id 'laser', copied from 'pistol'
/armed set laser damage 5
/armed set laser material hand    # use the item in your hand

/armed set laser cost-currency diamond
/armed set laser cost-amount 2
/armed set laser fire-rate 0.3
/armed set laser trail-particle FLAME
/armed description laser add &7Pew pew.
```



## Files

* `guns.yml` - every gun; the header documents each property
* `config.yml` - combat rules, item/shop layout and lore templates, currencies, sounds
* `messages.yml` - all text

## Version notes

Compiled against Spigot 1.8.8 and written to avoid APIs that differ between versions:

* materials are resolved by name with old/new aliases (`WOODEN_HOE` or `WOOD_HOE`)
* sounds are namespaced-key strings (`entity.arrow.shoot`), translated for 1.8 where a mapping exists
* particles, action bar and item tagging go through reflection (PersistentDataContainer on 1.14+, hidden name marker on 1.8-1.12, name matching on 1.13)
* the shop never touches `InventoryView`, which changed from class to interface in 1.21
* bullets are hitscan rays, not projectile entities

Limits: bullets stop at any solid block (use `settings.pass-through-materials` for glass etc.), hitboxes are 0.6 x 1.8 (1.5 sneaking) plus `settings.hitbox-expand`, and in-game edits rewrite the YAML (comments are kept only on servers whose YAML API preserves them, 1.18.2+).
