# CLAUDE.md — dregora-server-mods

Handoff for Claude Code. Read this, then `docs/00-overview.md`, then the doc for the module you're working on.

## What this repo is
Two **server-side-only** Forge mods for the owner's RLCraft Dregora 1.1.2b co-op server (Minecraft 1.12.2, Forge 14.23.5.2860, Java 8; ~6 concurrent players, 4 regulars).

| Folder | Mod | Mod ID | Kind |
|---|---|---|---|
| `warband/` | Warband | `warband` | normal mod — co-op QoL, passive observer |
| `dregorapatches/` | Dregora Patches | `dregorapatches` | coremod — mixin fixes for pack mods |

Each folder is its own Gradle project (own `gradlew`). No root build.

## Docs map
```
docs/00-overview.md          scope, rules, phases, commands  ← start here
docs/07-nc-fission-fix.md    Patches P1 (live lag problem)
docs/08-levelup2-furnace-scan.md  Patches P1 (steady 2.3 ms/tick)
docs/05-death-recap.md       Warband P2
docs/06-trick-shots.md       Warband P2
docs/03-killfeed.md          Warband P3
docs/04-friendly-fire.md     Warband P3
docs/ideas/                  parked — do not build unless asked
docs/reference/handoff-nc-fission-lag.md   original lag investigation (server paths, spark/rcon commands)
docs/reference/pack-mods.txt               exact mod jars on the server (pack-mods-client.txt: client)
docs/reference/mods-context.md             every mod the plan touches: versions, sources, classes, risks
docs/reference/spark-*-summary.txt         profile summaries (tools/spark_summary.py)
tools/spark_summary.py                     dependency-free .sparkprofile → text summary
```
Docs marked **⚠ verify** are API/behavior claims not yet confirmed against the real jars. Confirm before relying on them; update the doc with what you found.

## Hard rules
1. **Java 8 language level.** No `var`, no records, no Java 9+ APIs.
2. **Server-only.** Players install nothing. Vanilla/Forge clients without the jars must connect normally. Output only via vanilla chat components.
3. **Warband never imports or reflects into other mods**, never cancels/modifies events (exception: `04` friendly fire, config-gated, default off). Listen at `EventPriority.LOWEST`, `receiveCanceled = false`.
4. **Dregora Patches** is the only place for mixins. Targets in mod classes use `@Mixin(value = X.class, remap = false)` and **`require = 0` on every injector** (a renamed target must disable the patch, never crash the server). Mixin member prefix `dp$`. Register with FermiumBooter (already in the pack, 1.4.1) — **must not ship a hard dependency on MixinBooter**.
5. Chat component JSON < 32767 chars or the receiver gets kicked.
6. Never edit the live server's world files. Anything touching the server (deploy, restart, rcon) → ask the user first.

## Setup (P0)
Package root: **`io.github.tidefox.<modid>`** (`io.github.tidefox.warband`, `io.github.tidefox.dregorapatches`), same as `tidefox/unrest`.

Build layout: copy the pattern of **`tidefox/skinbridge`** (a working FermiumBooter coremod for this same server), not TemplateDevEnv:
1. Per folder: standalone RetroFuturaGradle `2.0.3` `build.gradle` + `settings.gradle` (GTNH maven, foojay toolchain resolver), Gradle wrapper 9.7, Java 8 toolchain, MCP `stable_39`.
2. `dregorapatches/` only:
   - Mixin annotations + annotation processor from MixinBooter `zone.rong:mixinbooter:11.18`, **compile-only** (`modUtils.enableMixins(..., 'mixins.dregorapatches.refmap.json')`), never shipped. FermiumBooter provides Mixin at runtime.
   - FermiumBooter 1.4.1 via CurseMaven `curse.maven:fermiumbooter-968474:7279112`, compile-only.
   - NuclearCraft and Level Up! 2 compile-only via CurseMaven (find file ids like skinbridge's `ci/find-fermium-id.py`) or `libs/` (git-ignored) as a fallback.
   - Jar manifest: `FMLCorePlugin = io.github.tidefox.dregorapatches.core.DregoraPatchesPlugin`, `FMLCorePluginContainsFMLMod = true`, `ForceLoadAsMod = true`.
   - Coremod: `FermiumCompat` (resource-lookup presence check, `enqueueMixin(boolean late, String)` isolated in one class), enqueue in the constructor with an `injectData` retry; `mixinsupport/` config plugin that logs and records applied mixins (`MixinState`), used by `/dpatch … stats`.
   - Mixin configs: `mixins.dregorapatches.vanilla.json` (early, refmap), `…nc.json` and `…levelup2.json` (late, `remap = false`); all `"required": false`, `"injectors": {"defaultRequire": 0}`.
3. `ci/smoke.sh` + `.github/workflows/build.yml` as in skinbridge: build, unit tests, boot a real Forge 2860 dedicated server with FermiumBooter + the jar, fail on mixin errors.
4. `./gradlew build` green in both folders; smoke test boots. Commit.

**P0 status: done.** Notes from doing it:
- Gradle runs on **Java 25** (RFG 2.0.3 plugin classes are Java 25 bytecode); compile/test use the Java 8 toolchain (foojay provisions it). Locally: `JAVA_HOME=<jdk25> ./gradlew build`.
- `./gradlew runServer` runs in `<folder>/run/` (accept the EULA once: `echo eula=true > run/eula.txt`). In `dregorapatches/` it copies FermiumBooter into `run/mods` and loads the coremod via `-Dfml.coreMods.load`.
- NuclearCraft compiles against 2.19d (`curse.maven:nuclearcraft-226254:9469401`, deobfuscated by RFG). Level Up! 2's CurseForge id wasn't reachable from the sandbox: drop the Level Up! 2 1.1.23 jar (server: `Level Up! 2-1.1.23-1.12.jar`) into `dregorapatches/libs/` (git-ignored) or set `levelup2_project_id`/`levelup2_file_id` in `gradle.properties` before writing the `08` mixin.
- FermiumBooter's classes are never visible in our coremod constructor; the early config is registered from `injectData` (see `docs/reference/mods-context.md` → FermiumBooter). `ci/smoke.sh` boots with both jar orders to keep that covered.
- The vanilla refmap is only generated once the early config lists a mixin; CI checks for it from then on.
- MixinExtras 0.5.0 ships inside FermiumBooter 1.4.1 (runtime). Add it compile-only when writing the first `@WrapOperation`.

## Phase order
| Phase | Work | Acceptance |
|---|---|---|
| P0 | setup above | builds + dev server boots |
| **P1** | `dregorapatches`, plan decided in `docs/07 §3` + `docs/08`: owner updates NuclearCraft to 2.19d and records a baseline (no code) → you build one jar: NC **reactor freeze + claims** (fission controllers/ports/active coolers don't tick unless the reactor is claimed via `/reactor`; estimated coords by default, ops get `-exact` and keep-loaded; on), NC **chunk guard** (`ChunkProviderServer` hooks scoped to NC ticks; off by default), Level Up! 2 **furnace scan fix** (on), `/dpatch nc stats`, `/dpatch chunkloads` → owner measures → owner turns the guard on → measures again | no 45 s spikes over 15 min tickmonitor vs. the 2.19d baseline; a claimed reactor forms and runs; radiation near ruins unchanged |
| P2 | `warband`: core (`ProjectileOrigin`, `/wb debug damage`, `/wb debug kill`, config) → `05` recap → `06` trick shots | tests listed in each doc |
| P3 | `warband`: `03` kill feed, then `04` friendly fire | tests listed in each doc |

RLC_Nuclear check: done — not installed on server or client (Dregora 1.2.0-beta-2 only; re-check if the server moves to 1.2.x). Keep Chunk Pregenerator idle during P1 measurements.

## Facts already verified (don't re-research)
- Owner: no player-built fission reactors exist; no chunk loaders in the pack; NC is only used as far as progression needs.
- Old fission controller only runs while all six casing faces are present (`complete == 1`); heat only changes while running → an unloaded part pauses the reactor, it can't melt down from it.
- Profiles (`docs/07 §1.2`): ≈ 90% of main-thread disk chunk loads come from NC tile entities (`TileDummy.isMaster`, scrubber `isOcclusive`, port/buffer/generator neighbour pushes). Linked ruin ports call `getMaster()` every tick from `pushEnergy`, keeping controller chunks loaded; ruins keep themselves loaded with no player or chunk loader. `findController` cost is mostly `getTileEntity → getPendingTileEntityAt`, not `BlockFinder`.
- NC radiation (`RadiationHandler.updateWorldRadiation`) only simulates loaded chunks, reads TE inventories directly (not via TE ticking) and spreads only to loaded neighbours.
- Level Up! 2 `FurnaceEfficiencyBonus.doFurnaceTicks` streams the whole `loadedTileEntityList` every world tick calling `hasCapability`; the capability is only attached to `TileEntityFurnace` / `TileEntityBrewingStand` subclasses (source: BeetoGuy/LevelUp2 — ⚠ verify against the 1.1.23 jar).
- NuclearCraft 2.19a = commit `37cc6e3` on branch `1.12.2` of `tomdodd4598/NuclearCraft`. `TileFissionPort.onAdded → findController` (4 orientations × up to `fission_max_size+2` per axis via `BlockFinder.find → world.getBlockState`); `findMaster()` is empty for ports; `NCTile.isAdded`, `TileDummy.masterPosition`, `NCConfig.fission_max_size` are public. `TileActiveCooler.spreadFluidToSide` and `TileFissionPort.pushPortEnergyToSide` call `world.getTileEntity(neighbor)` with no loaded-check. `TileDummy.hasMaster()` exists (null `masterPosition` → false, no lookup). `TileFissionPort`, `TileDummy`, `TileActiveCooler` are identical in 2.19a and 2.19d (`33568a8`); only `BlockFinder.java` differs → one mixin covers both.
- FermiumBooter API: `fermiumbooter.FermiumRegistryAPI.enqueueMixin(...)`, called from the `IFMLLoadingPlugin` constructor.
- Pack has First Aid 1.6.22 (per-body-part health — affects `05`), ElenaiDodge, Scaling Health 1.3.42, Champions, InfernalMobs, Lycanites 2.0.8.10, Spartan Weaponry 1.6.1, switchbow, RLCombat, ItemPhysic Full, JEI 4.16.1.1012.

## Testing & deploy
- Unit-test pure logic with JUnit (formatters, aggregation, distance tiers, FailCache invalidation, FF curve).
- In-game: `./gradlew runServer` + a vanilla 1.12.2 Forge client; then the user's local Dregora instance.
- Live server (owner runs it; **ask before touching**): container `minecraft` under rootless podman on host `kyuubi`; extra jars go in `~/srv/minecraft-extra/mods/` (copied into `/data/mods` at start); logs `journalctl --user -u minecraft`; rcon via `podman exec minecraft rcon-cli "<cmd>"`; spark is installed (`spark tickmonitor --threshold-tick 150`, `spark profiler start --only-ticks-over 1000 --timeout 300`).

## Conventions
- Logger: `LogManager.getLogger("warband")` / `("dregorapatches")`.
- Config: Forge `@Config`, one class per mod, module toggles `<module>.enabled`.
- Lang keys under `assets/<modid>/lang/en_us.lang`, but **server-sent text must be literal** (clients don't have the lang file) — use `TextComponentString`, not `TextComponentTranslation`, for our own strings.
- Small commits per step; update the module doc's "⚠ verify" items as they're resolved.

## Open questions for the user
- Friendly fire numbers (curve, lethal floor) — defaults in `docs/04`.
