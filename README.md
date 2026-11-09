# Magic Treasures (Hytale)

Magic jewelry, gems and spells for Hytale: the Hytale port of the
[Minecraft mod](https://www.curseforge.com/minecraft/mc-mods/magic-treasures).

Early work. So far: jewelry items with stats from data (`/mt give`, `/mt info`), plus the feasibility tests (`/mt cast`, `/mt wear` and a test ring).

## Jewelry data

Materials, styles and gems are JSON under `src/main/resources/Server/MagicTreasures/` (`Materials`, `Styles`, `Gems`), and other asset packs can add their own.
The jewelry items themselves are generated:

```
python tools/generate_jewelry.py          # the small development set
python tools/generate_jewelry.py --all    # every combination
```

It writes the item assets, the matching `MagicTreasures/Jewelry` definitions and a marked block of `server.lang`.

## Building

Requires Java 25. Targets the Hytale `release` patchline, server 0.7.6.

```
gradlew.bat build
gradlew.bat runServer
```

`runServer` starts a dev server: run `/auth login browser` in its console, then connect to `127.0.0.1:5520`.
`manifest.json` is generated from `gradle.properties`; run `gradlew.bat updatePluginManifest` after changing them.

Built from the [HytaleModding plugin template](https://github.com/HytaleModding/plugin-template).

## License

LGPL-3.0-or-later. See [LICENSE.txt](LICENSE.txt).
