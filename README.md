# Magic Treasures (Hytale)

Magic jewelry, gems and spells for Hytale: the Hytale port of the
[Minecraft mod](https://www.curseforge.com/minecraft/mc-mods/magic-treasures).

Early work. Right now the plugin only holds feasibility tests (`/mt cast`, `/mt wear` and a test ring).

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
