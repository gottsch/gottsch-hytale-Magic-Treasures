<!-- TITLE IMAGE: upload to the wiki's images folder, then:
<p align="center"><img src="https://raw.githubusercontent.com/wiki/gottsch/gottsch-hytale-MagicTreasures/images/magictreasures-title.png" alt="Magic Treasures" width="500"></p>
-->

# Magic Treasures (Hytale)

Magic jewelry, gems and spells for Hytale. Find or craft rings, necklaces and bracers, cut gems and set them, imbue spells from scrolls, and wear up to four pieces at once. Worn jewelry casts its spells for you when they're useful, from its own charge.

This is the Hytale version of the [Minecraft mod](https://www.curseforge.com/minecraft/mc-mods/magic-treasures) of the same name.
Download it from [CurseForge](https://www.curseforge.com/hytale/mods/magic-treasures). The [wiki](https://github.com/gottsch/gottsch-hytale-MagicTreasures/wiki) lists every spell, metal and gem with its numbers.

## Features

- **Jewelry:** rings, necklaces and bracers in copper, silver and gold, plain or Great, with or without one of seven gems. Each piece has its own charge, uses, recharges, repairs, spell level and rune sockets.
- **Treasures Case:** wear 2 rings, a necklace and a bracer. The case works from anywhere in the inventory; worn pieces show on the character.
- **Spells:** 33 spells in 9 families (Healing, Speed, Strength, Cat Sight, Wards, Weapons, Strikes, Vampiric, Drain auras). They cast themselves when hurt, on a hit, when taking elemental damage, or whenever ready. The metal and gem scale every spell.
- **Tooltips with real numbers:** each spell shows its total on that piece and where it comes from (base, metal, gem).
- **Gem cutting:** cut raw gems with a Gem Cutter's Kit into Flawed, Fine, Flawless or Perfect. A gem cutting skill grows with practice; cuts at the edge of it can shatter the gem.
- **Element powers:** gems and metals roll resistances, statuses on hit, elemental damage added to hits, and critical hit chance.
- **Runes:** socket a core rune in a piece and the piece boosts that rune when you cast it.
- **Worn-jewelry panel:** each worn piece's charge and spell cooldowns.
- **Loot:** chests, monsters and bosses, added alongside the game's own loot without replacing its tables. One named ring, the Vampire Lich's Ring, drops from bosses and the deepest chests.

## Installing

1. Put `MagicTreasures-1.0.0.jar` in your Hytale `UserData\Mods` folder (or your server's `mods` folder).
2. For a local world, enable it in the world's `config.json` (`UserData\Saves\<your world>`):
   ```json
   "Mods": {
     "gottsch:MagicTreasures": { "Enabled": true }
   }
   ```
3. Start the world and craft a Treasures Case (Workbench, Tinkering).

Requires Hytale server 0.7.7 or newer in the 0.7 line. No other mods needed.

## Commands

All under `/magictreasures` (alias `/mt`). `worn` and `cutting` are open to everyone; the others need operator permission:

- `/mt worn`: what you're wearing, with element totals
- `/mt cutting`: your gem cutting level
- `/mt give`: give yourself a piece of jewelry
- `/mt info`: the held piece's stats and saved state
- `/mt hits`: show the damage of each hit you deal (toggle)
- `/mt setcutting <level>`: set your gem cutting level
- `/mt loot`: roll Magic Treasures loot for a vanilla drop list

## Notes

- Frost, Storm and Stone Ward are in the mod but kept out of loot: almost nothing in Hytale deals Ice, Lightning or Earth damage yet.
- Tested solo. Please report anything odd with several players.

## Data and tools

Everything is data, so asset packs can add their own:

- `src/main/resources/Server/MagicTreasures/`: `Materials`, `Styles`, `Gems`, `Spells`, `Jewelry` (definitions) and `Loot` (tables matched to vanilla drop lists by regex).
- The jewelry items, definitions, scrolls, cut gems and their `server.lang` text are generated:
  ```
  python tools/generate_jewelry.py          # the starter set
  python tools/generate_jewelry.py --all    # every combination
  ```
  It empties its output folders first. Close the dev server before running it, since the server runs from `src/main/resources`.
- Named pieces (such as the Vampire Lich's Ring) are authored in `tools/named_pieces.json`; `generate_jewelry.py` writes them too.
- The art scripts in `tools/` (`make_*.py`) take `--assets <Assets.zip>`.

## Building

Requires Java 25. Targets the Hytale `release` patchline, server 0.7.7.

```
gradlew.bat build
gradlew.bat runServer
```

`hytaleHomeOverride` in `gradle.properties` points at the local Hytale install's `Assets.zip`.
`manifest.json` is generated from `gradle.properties`; run `gradlew.bat updatePluginManifest` after changing them.
`runServer` starts a dev server: run `/auth login browser` in its console, then connect to `127.0.0.1:5520`.

Built from the [HytaleModding plugin template](https://github.com/HytaleModding/plugin-template).

## Links

- [CurseForge](https://www.curseforge.com/hytale/mods/magic-treasures)
- [Wiki](https://github.com/gottsch/gottsch-hytale-MagicTreasures/wiki)
- [Issues and suggestions](https://github.com/gottsch/gottsch-hytale-MagicTreasures/issues)
- [Discord](https://discord.gg/CpWXamx)
- Support the mod: [Ko-fi](https://ko-fi.com/gottsch) or [PayPal](https://www.paypal.com/paypalme/gottschsmods)

## License

LGPL-3.0-or-later. See [LICENSE.txt](LICENSE.txt).
