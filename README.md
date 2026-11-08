# dregora-server-mods

Two server-side-only Forge 1.12.2 mods for an RLCraft Dregora co-op server. Players install nothing.

| Folder | Mod | What |
|---|---|---|
| `dregorapatches/` | Dregora Patches (coremod, needs FermiumBooter ≥ 1.4.1) | performance fixes for pack mods (NuclearCraft, Level Up! 2) |
| `warband/` | Warband | co-op QoL: death recap, trick shots, kill feed, friendly fire |

Design docs: `docs/00-overview.md`.

## Build
Each folder is its own Gradle project. Gradle needs Java 25 (RetroFuturaGradle 2.x); the mods compile for Java 8 via a toolchain.

```bash
cd dregorapatches && ./gradlew build
```

```bash
cd warband && ./gradlew build
```

Release jars: `<folder>/build/libs/<modid>-<version>.jar` (not `-dev.jar`).

Dev server: `./gradlew runServer` in either folder (runs in `run/`, accept the EULA in `run/eula.txt`).

Smoke test (real Forge 2860 server with FermiumBooter and both jars), as run by CI:

```bash
ci/smoke.sh <forge-server-dir> dregorapatches/build/libs/dregorapatches-0.1.0.jar warband/build/libs/warband-0.1.0.jar <FermiumBooter-1.4.1.jar>
```

## Install (server)
Copy both release jars into `mods/`. Dregora Patches needs FermiumBooter (already in the pack); without it the server still starts and nothing is patched.
