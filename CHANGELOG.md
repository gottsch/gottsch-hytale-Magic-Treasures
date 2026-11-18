# Changelog for Magic Treasures for Hytale

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.0] - Unreleased

### Added

- **Defensive spells**, cast when you're hurt:
  - Stoneskin (level 2): takes 25% off physical and projectile damage for 30 seconds.
  - Thorns (level 3): hands 30% of a melee hit back to the attacker.
  - Barrier (level 5): when a hit leaves you under 35% health, heals you 10 and takes 50% off all physical, projectile and elemental damage for 8 seconds.
- Their spell scrolls are in chests and on monsters with the others.
- **Thornguard:** a named Legendary iron bracer set with a perfect ruby, ringed with thorns. Its Thorns returns 60% of melee hits, as often as once a second, never runs out and never breaks. Worn, it keeps shorter thorns on the outer side of the wrist. Found on bosses and in the deepest chests.
- **Amulet of the Last Stand:** a named Legendary gold necklace, a diamond set on a shield. Its Barrier, when a hit leaves you under 35% health, heals you 30 and takes 50% off all physical, projectile and elemental damage for 8 seconds, every 2 minutes. It never runs out or breaks, under a Curse of Vanishing. Worn, its shield hangs on your chest. Found on bosses and in the deepest chests.
- **Curse of Weariness:** each cast of the cursed piece takes 2 stamina, never below empty. Thornguard carries it.
- Worn iron pieces show on your character (iron, and iron with a ruby, for now).
- **Server settings**, in `mods/gottsch_MagicTreasures/MagicTreasures.json`, written on the first start: how often chests, monsters and bosses get Magic Treasures loot, how fast worn pieces refill their charge, and spell cooldowns. Each is a multiplier, 1.0 being the mod as shipped.
- **Brazilian Portuguese (pt-BR) translation**: item names and descriptions, tooltips, the imbue, gem cutting and Treasures Case pages, the worn-jewelry panel, messages and command descriptions. The output of the operator commands (`info`, `hits`, `loot`, `give`, `setcutting`) stays in English.

### Changed

- Great rings are engraved: a bevelled band with a vine running around it, and beads along the rims and the gem's setting, at twice the detail of plain rings. Their icons show the new look.
- Tooltips and messages show in each player's own language. A piece made before this update keeps its English tooltip until its charge or state next changes.
- The worn-jewelry panel no longer shortens spell names ("Gr. Healing" is "Greater Healing").

### Fixed

- Emerald descriptions read "A emerald".

## [1.1.0] - 2026-11-16

### Changed

- Necklaces hang on a leather cord instead of a metal chain, so the metal pendant stands out. This covers the held necklace, its icon and the worn necklace.
- A piece's element powers show where they come from, and powers of the same kind add into one line, as they do in play: "Poison resistance +21% (silver 14%, emerald 7%)", "Burn on hit 10% (ruby)". Pieces made before this update show their totals without the source.

### Fixed

- Tooltips showed a socketed rune's item id ("Rune_Enrage") instead of its name ("Enrage").
- Rune names with two words ran together ("GroundSlam") in `/mt worn`, the worn-jewelry panel, the imbue page and the rune boost message.
- A resistance from both the metal and the gem showed as two separate lines.

## [1.0.0] - 2026-11-15

First version: magic jewelry, gems and spells. Targets Hytale server 0.7.7.

### Added

- **Jewelry:** rings, necklaces and bracers in copper, silver and gold, plain or Great, with or without a gem (144 pieces). Each piece has its own charge, uses, recharges, repairs, spell level and rune sockets, set by its metal, style and gem.
- **Treasures Case:** holds the jewelry you wear, 2 rings, a necklace and a bracer, from anywhere in your inventory. Hold it and right-click to put pieces on and take them off. Crafted at the Workbench (Tinkering) from 2 Light Leather, 1 Leather Strap and 1 Iron Bar.
- Worn jewelry shows on your character: rings on your fingers, the necklace over your shoulders, the bracer on your wrist.
- A worn-jewelry panel lists each worn piece with its charge and the seconds until each spell is ready.
- **33 spells** in 9 families, imbued from spell scrolls, one per piece:
  - Healing: Lesser Healing, Healing, Greater Healing;
  - Speed, Greater Speed; Quick Strength, Strength, Greater Strength; Cat Sight;
  - Wards: Fire Ward, Venom Ward, Greater Ward;
  - Weapons: Flame, Frost, Storm, Stone and Venom Weapon (25% of each hit added as an element, with a status);
  - Strikes: Fire, Frost, Storm, Stone and Venom Strike, and their Greater versions (a flat elemental hit);
  - Vampiric Kiss, Bite and Embrace (drain what you hit and heal);
  - Lesser Drain, Drain and Greater Drain (an aura draining nearby hostile mobs).
- Spells cast themselves when they are useful: when you're hurt, when you hit, when you take elemental damage, or whenever ready. The metal and gem scale each spell's cost, cooldown, strength, duration and range.
- Tooltips show each spell's real numbers on that piece: the total, its base and what the metal and gem add, then cost, cooldown and duration.
- Worn pieces refill their charge over 8 to 10 minutes, depending on the metal.
- **Gem cutting:** a Gem Cutter's Kit (Workbench, Tools: 2 Iron Bar, 1 Light Leather, 1 Topaz) cuts raw gems into Flawed, Fine, Flawless or Perfect. A gem cutting skill (levels 1 to 10) grows with each cut; harder gems need a higher level, and a cut at the edge of your skill can shatter the gem.
- **Seven gems** (Topaz, Emerald, Ruby, Zephyr, Sapphire, Diamond, Voidstone), each with its own element powers: resistances, statuses on hit, elemental damage added to hits, critical hit chance. A better cut adds more charge and recharges and unlocks more of its powers; a Flawed one may carry a weakness.
- **Working on jewelry:** right-click a spell scroll, cut gem, core rune, crystal or another piece of jewelry and choose a piece to imbue, set, socket, recharge or repair.
- **Runes:** a core rune socketed in a worn piece boosts that rune when you cast it (cost, cooldown, damage).
- **The Vampire Lich's Ring:** a named Legendary ring with Vampiric Embrace that never runs out or breaks, under a Curse of Vanishing. Found on bosses and in the deepest chests.
- **Loot:** jewelry, spell scrolls, cut gems and recipe pages in chests (better the deeper you go), on monsters and on bosses. Jewelry is crafted at the Workbench (Tinkering) from a recipe page and 4 copper, silver or gold bars.
- Commands under `/magictreasures` (alias `/mt`): `worn` and `cutting` for everyone; `give`, `info`, `hits`, `setcutting` and `loot` for operators.

### Notes

- Loot is added next to the game's own, without replacing its loot tables, so it works alongside mods that change them.
- Frost, Storm and Stone Ward are in the mod but kept out of loot: almost nothing in Hytale deals Ice, Lightning or Earth damage yet.
