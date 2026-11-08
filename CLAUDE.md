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
docs/reference/pack-mods.txt               exact mod jars on the server
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
1. Ask the user for the Java package root (`io.github.<handle>`) — don't guess.
2. Into each folder, copy CleanroomMC **TemplateDevEnv** (https://github.com/CleanroomMC/TemplateDevEnv; RetroFuturaGradle, Gradle 8, Java 8 toolchain). Set in `gradle.properties`:
   - `warband/`: `mod_id = warband`, `mod_name = Warband`, `root_package = <root>`, `use_mixins = false`, `enable_junit_testing = true`.
   - `dregorapatches/`: `mod_id = dregorapatches`, `mod_name = Dregora Patches`, `is_coremod = true`, `coremod_includes_mod = true`, `coremod_plugin_class_name = <root>.dregorapatches.DregoraPatchesPlugin`, `use_mixins = true` (annotation processor), `mixin_configs = mixins.dregorapatches.nc.json`.
3. `dregorapatches/libs/` (git-ignored, user supplies from the server's `mods/`): `nuclearcraft-1.12.2-2.19a.jar`, `FermiumBooter-1.4.1.jar` → `compileOnly` + dev runtime.
4. ⚠ Decide dev-runtime mixin loader: TemplateDevEnv's `use_mixins` pulls **MixinBooter**; the pack uses **FermiumBooter**. Either run dev with FermiumBooter and exclude MixinBooter from runtime, or support both loaders. Production jar must work with FermiumBooter 1.4.1 alone. Confirm `FermiumRegistryAPI.enqueueMixin(boolean late, String)` exists in 1.4.1 (javap the jar).
5. `./gradlew build` and `./gradlew runServer` green in both folders. Commit.

## Phase order
| Phase | Work | Acceptance |
|---|---|---|
| P0 | setup above | builds + dev server boots |
| **P1** | `dregorapatches`, plan decided in `docs/07 §3` + `docs/08`: owner updates NuclearCraft to 2.19d and records a baseline (no code) → you build one jar: NC **result cache** on `TileFissionPort.findController()V` (on), NC **chunk guard + search deferral** (`ChunkProviderServer` hooks scoped to NC ticks, off by default), Level Up! 2 **furnace scan fix** (on), `/dpatch nc stats`, `/dpatch chunkloads` → owner measures cache → owner turns the guard on → measures again | no 45 s spikes over 15 min tickmonitor vs. the 2.19d baseline; player-built reactor still forms; radiation near ruins unchanged |
| P2 | `warband`: core (`ProjectileOrigin`, `/wb debug damage`, `/wb debug kill`, config) → `05` recap → `06` trick shots | tests listed in each doc |
| P3 | `warband`: `03` kill feed, then `04` friendly fire | tests listed in each doc |

Before P1 code: ask the user to run `unzip -l RLC_Nuclear*.jar` (Dregora 1.2.0-beta-2 adds it) — if it already mixes into `TileFissionPort`, stop and reassess.

## Facts already verified (don't re-research)
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
- Java package root / GitHub handle.
- Friendly fire numbers (curve, lethal floor) — defaults in `docs/04`.
