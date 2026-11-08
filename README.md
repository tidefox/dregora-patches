# dregora-patches

**Dregora Patches**: one server-side-only Forge 1.12.2 mod for an RLCraft Dregora co-op server. Players install nothing.

- **Patches** (mixins, needs FermiumBooter ≥ 1.4.1): NuclearCraft ruin reactor freeze + `/reactor` claims, NC chunk guard, Level Up! 2 furnace scan fix.
- **Features** (plain Forge event observers): trick shot callouts + `/trickshots`, kill feed (planned).

Every module has its own switch in `config/dregorapatches.cfg`. Design docs: `docs/00-overview.md`.

Adaptive friendly fire lives in its own repo, Friendly Fire Rework (`tidefox/friendly-fire-rework`); spec in `docs/handoff/friendly-fire-rework.md`.

## Build
Gradle needs Java 25 (RetroFuturaGradle 2.x); the mod compiles for Java 8 via a toolchain.

```bash
./gradlew build
```

Release jar: `build/libs/dregorapatches-<version>.jar` (not `-dev.jar`).

Dev server: `./gradlew runServer` (runs in `run/`, accept the EULA in `run/eula.txt`).

Smoke test (real Forge 2860 server with FermiumBooter and the jar), as run by CI:

```bash
ci/smoke.sh <forge-server-dir> build/libs/dregorapatches-0.1.0.jar <FermiumBooter-1.4.1.jar>
```

## Install (server)
Copy the release jar into `mods/`. The patches need FermiumBooter (already in the pack); without it the server still starts, nothing is patched, and the features still work.
