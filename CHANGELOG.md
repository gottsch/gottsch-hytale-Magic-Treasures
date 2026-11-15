# Changelog for Magic Treasures for Hytale

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - Unreleased

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
