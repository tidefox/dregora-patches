# CLAUDE.md — dregora-patches

Handoff for Claude Code. Read this, then `docs/00-overview.md`, then the doc for the module you're working on.

## What this repo is
One **server-side-only** Forge mod for the owner's RLCraft Dregora 1.1.2b co-op server (Minecraft 1.12.2, Forge 14.23.5.2860, Java 8; ~6 concurrent players, 4 regulars): **Dregora Patches** (`dregorapatches`), a coremod jar with
- **patches**: mixin fixes for pack mods (NuclearCraft, Level Up! 2), and
- **features**: passive Forge event observers for this server (trick shots, kill feed).

Single Gradle project at the repo root. Repo: `tidefox/dregora-patches` (formerly `dregora-server-mods`).

2026-11-08 restructure: Warband was dissolved. Trick shots + kill feed moved in here, the death recap was dropped, and friendly fire moved to its own repo, **Friendly Fire Rework** (`tidefox/friendly-fire-rework`; spec: `docs/handoff/friendly-fire-rework.md`). Don't build recap or friendly fire here.

## Docs map
```
docs/00-overview.md          scope, rules, phases, commands  ← start here
docs/07-nc-fission-fix.md    Patches P1 (live lag problem)
docs/08-levelup2-furnace-scan.md  Patches P1 (steady 2.3 ms/tick)
docs/06-trick-shots.md       feature P2
docs/03-killfeed.md          feature P3 (code done)
docs/handoff/friendly-fire-rework.md  spec for the separate friendly-fire repo — not built here
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
2. **Server-only.** Players install nothing. Vanilla/Forge clients without the jar must connect normally. Output only via vanilla chat components.
3. **Features** (`trick/`, `killfeed/`, `combat/`, `util/`) never import or reflect into other mods, never use mixins (one exception: the kill feed's read-only vanilla chat tap, `docs/03 §2.5`), never cancel/modify events. Listen at `EventPriority.LOWEST`, `receiveCanceled = false` (`/dpatch debug` reads canceled events while armed). Reading damage *to players* must happen at `LOW`: First Aid cancels every player hurt at LOWEST.
4. **Patches**: mixins live only in `mixin/`. Targets in mod classes use `@Mixin(value = X.class, remap = false)` and **`require = 0` on every injector** (a renamed target must disable the patch, never crash the server). Mixin member prefix `dp$`. Register with FermiumBooter (already in the pack, 1.4.1) — **must not ship a hard dependency on MixinBooter**.
5. Chat component JSON < 32767 chars or the receiver gets kicked.
6. Never edit the live server's world files. Anything touching the server (deploy, restart, rcon) → ask the user first.

## Setup (P0)
Package root: **`io.github.tidefox.dregorapatches`**, same scheme as `tidefox/unrest`.

Build layout: copy the pattern of **`tidefox/skinbridge`** (a working FermiumBooter coremod for this same server), not TemplateDevEnv:
1. Root project: standalone RetroFuturaGradle `2.0.3` `build.gradle` + `settings.gradle` (GTNH maven, foojay toolchain resolver), Gradle wrapper 9.7, Java 8 toolchain, MCP `stable_39`.
2. Coremod specifics:
   - Mixin annotations + annotation processor from MixinBooter `zone.rong:mixinbooter:11.18`, **compile-only** (`modUtils.enableMixins(..., 'mixins.dregorapatches.refmap.json')`), never shipped. FermiumBooter provides Mixin at runtime.
   - FermiumBooter 1.4.1 via CurseMaven `curse.maven:fermiumbooter-968474:7279112`, compile-only.
   - NuclearCraft and Level Up! 2 compile-only via CurseMaven (find file ids like skinbridge's `ci/find-fermium-id.py`) or `libs/` (git-ignored) as a fallback.
   - Jar manifest: `FMLCorePlugin = io.github.tidefox.dregorapatches.core.DregoraPatchesPlugin`, `FMLCorePluginContainsFMLMod = true`, `ForceLoadAsMod = true`.
   - Coremod: `FermiumCompat` (resource-lookup presence check, `enqueueMixin(boolean late, String)` isolated in one class), enqueue in the constructor with an `injectData` retry; `mixinsupport/` config plugin that logs and records applied mixins (`MixinState`), used by `/dpatch … stats`.
   - Mixin configs: `mixins.dregorapatches.vanilla.json` (early, refmap), `…nc.json` and `…levelup2.json` (late, `remap = false`); all `"required": false`, `"injectors": {"defaultRequire": 0}`.
3. `ci/smoke.sh` + `.github/workflows/build.yml` as in skinbridge: build, unit tests, boot a real Forge 2860 dedicated server with FermiumBooter + the jar, fail on mixin errors.
4. `./gradlew build` green; smoke test boots. Commit.

**P0 status: done.** Notes from doing it:
- Gradle runs on **Java 25** (RFG 2.0.3 plugin classes are Java 25 bytecode); compile/test use the Java 8 toolchain (foojay provisions it). Locally: `JAVA_HOME=<jdk25> ./gradlew build`.
- `./gradlew runServer` runs in `run/` (accept the EULA once: `echo eula=true > run/eula.txt`). It copies FermiumBooter into `run/mods` and loads the coremod via `-Dfml.coreMods.load`.
- NuclearCraft compiles against **2.19a** (`curse.maven:nuclearcraft-226254:6151363`, deobfuscated by RFG), the version the server and clients run. Level Up! 2 (CurseForge "Level Up! Reloaded") compiles from CurseMaven `curse.maven:levelup2-251136:2763516` (same sha1 as the server jar). `libs/` (git-ignored) is only a fallback; never commit mod jars.
- FermiumBooter's classes are never visible in our coremod constructor; the early config is registered from `injectData` (see `docs/reference/mods-context.md` → FermiumBooter). `ci/smoke.sh` boots with both jar orders to keep that covered.
- The vanilla refmap is only generated once the early config lists a mixin; CI checks for it from then on.
- MixinExtras 0.5.0 ships inside FermiumBooter 1.4.1 (runtime). Add it compile-only when writing the first `@WrapOperation`.

## Phase order
| Phase | Work | Acceptance |
|---|---|---|
| P0 | setup above | builds + dev server boots |
| **P1** | `dregorapatches`, plan decided in `docs/07 §3` + `docs/08`: owner records a baseline on the current NuclearCraft 2.19a (no code; NC stays on 2.19a, see `docs/07 §3` step 1) → you build one jar: NC **reactor freeze + claims** (fission controllers/ports/active coolers don't tick unless the reactor is claimed via `/reactor`; estimated coords by default, ops get `-exact` and keep-loaded; on), NC **chunk guard** (`ChunkProviderServer` hooks scoped to NC ticks; off by default), Level Up! 2 **furnace scan fix** (on), `/dpatch nc stats`, `/dpatch chunkloads` → owner measures → owner turns the guard on → measures again | no 45 s spikes over 15 min tickmonitor vs. the 2.19a baseline; a claimed reactor forms and runs; radiation near ruins unchanged |
| P2 | features: core (`ProjectileOrigin`, `/dpatch debug damage`, `/dpatch debug kill`, config) → `06` trick shots | tests listed in the doc |
| P3 | features: `03` kill feed (one follow-up line: weapon · distance · final hit · fight time) | tests listed in the doc |

**P1 status: code done** (2026-11-08, `docs/07 §4.6`): freeze + claims, chunk guard, Level Up! 2 scan fix, `/reactor`, `/dpatch nc|levelup2 stats`, `/dpatch chunkloads`; all mixins apply on a real Forge 2860 server with NC 2.19a + Level Up! 2 (`ci/smoke.sh` now boots with them). Live step 2 (freeze on, guard off) measured 2026-11-08 with 5–6 players (`docs/07 §4.7`): 1–2.6 s spikes gone, max slow tick 397 ms (900-tick autosave); Level Up! 2 scan 0.09–0.14 ms/tick. No fresh no-jar baseline was taken; compared against the old profiles. Profile: `TileFissionPort` gone (< 0.2 %), 900-tick slow ticks = vanilla autosave, Level Up! 2 scan 0.44 % (0.22 ms/tick, target < 0.1 % missed, `docs/08 §4`). Waiting on: owner decides on guard on → measure again (expected gain small: NC disk loads already ≈ 0.16 s / 30 min). Still untested (needs a client): claim prompt/claim/unclaim, keep-loaded, owned-furnace bonus. Gotchas found: vanilla `EmptyChunk` is client-only (a mixin referencing it crashes the dedicated server even with `require = 0`); Forge-added methods like `loadChunk(IILRunnable)` need `remap = false`; `TileFissionController` can't be compiled against (OC/IC2 interfaces), read it by reflection.

**P2 status: code done** (2026-11-08; built in Warband, moved into Dregora Patches the same day): core (`combat/ProjectileOrigin`/`ShotTag`, `combat/DebugProbe` = `/dpatch debug damage|kill|off`, `util/Chat`, `util/Fmt`, `PatchesConfig.trickshots`), `06` trick shots (`/trickshots [player]`, records in `world/data/dregorapatches_trickshots.dat`); smoke boots it and runs `/dpatch debug kill`, `/trickshots`. Still untested (needs a client): every in-game test in `06 §3`. Next: run `/dpatch debug damage` and `/dpatch debug kill` on the live pack (Blight/Champions/dragon markers → `trickshots.mobs` / `nbtMarkers`). Found: First Aid 1.6.22 cancels every player `LivingHurtEvent` at LOWEST (→ `LivingDamageEvent` never fires for players); `LivingDamageEvent`'s amount is after absorption.

**P3 status: code done** (2026-11-08): `killfeed/` adds one line under the existing death message for PvP kills and announced-mob kills (`docs/03 §3`). Still untested (needs a client): every test in `03 §3`; message order under Scaling Health's Blight line.
2026-11-09, dynamic kill feed (`docs/03 §2.5`): owner saw pack kill lines with no follow-up. Now `killfeed/ChatTap` (vanilla `PlayerList`/`EntityPlayerMP` send hooks) sees every system line; any line naming the dead mob/player within `lineWaitTicks` gets the follow-up, whatever mod sent it. Notable mobs nothing announced (bosses, Blights, `killfeed.announceMobs` = Ice and Fire dragons + sea serpent, `announceMinMaxHealth`) get our own `[Fire Dragon] was slain by Alice` + follow-up. Credit also goes to a pet's owner and, for deaths with no attacker (fire/bleed/fall), to the last hitter within `lingerCreditSec`. Smoke: both hooks apply. ⚠ verify in-game: IaF registry ids, dragon death fires once.

RLC_Nuclear check: done — not installed on server or client (Dregora 1.2.0-beta-2 only; re-check if the server moves to 1.2.x). Keep Chunk Pregenerator idle during P1 measurements.

## Facts already verified (don't re-research)
- Owner: no player-built fission reactors exist; no chunk loaders in the pack; NC is only used as far as progression needs.
- Old fission controller only runs while all six casing faces are present (`complete == 1`); heat only changes while running → an unloaded part pauses the reactor, it can't melt down from it.
- Profiles (`docs/07 §1.2`): ≈ 90% of main-thread disk chunk loads come from NC tile entities (`TileDummy.isMaster`, scrubber `isOcclusive`, port/buffer/generator neighbour pushes). Linked ruin ports call `getMaster()` every tick from `pushEnergy`, keeping controller chunks loaded; ruins keep themselves loaded with no player or chunk loader. `findController` cost is mostly `getTileEntity → getPendingTileEntityAt`, not `BlockFinder`.
- NC radiation (`RadiationHandler.updateWorldRadiation`) only simulates loaded chunks, reads TE inventories directly (not via TE ticking) and spreads only to loaded neighbours.
- Level Up! 2 `FurnaceEfficiencyBonus.doFurnaceTicks` streams the whole `loadedTileEntityList` every world tick calling `hasCapability`; the capability is only attached to `TileEntityFurnace` / `TileEntityBrewingStand` subclasses (✔ verified against the 1.1.23 jar, `docs/08 §1.2`).
- NuclearCraft 2.19a = commit `37cc6e3` on branch `1.12.2` of `tomdodd4598/NuclearCraft`. `TileFissionPort.onAdded → findController` (4 orientations × up to `fission_max_size+2` per axis via `BlockFinder.find → world.getBlockState`); `findMaster()` is empty for ports; `NCTile.isAdded`, `TileDummy.masterPosition`, `NCConfig.fission_max_size` are public. `TileActiveCooler.spreadFluidToSide` and `TileFissionPort.pushPortEnergyToSide` call `world.getTileEntity(neighbor)` with no loaded-check. `TileDummy.hasMaster()` exists (null `masterPosition` → false, no lookup). `TileFissionPort`, `TileDummy`, `TileActiveCooler` are identical in 2.19a and 2.19d (`33568a8`); only `BlockFinder.java` differs → one mixin covers both.
- FermiumBooter API: `fermiumbooter.FermiumRegistryAPI.enqueueMixin(...)`, called from the `IFMLLoadingPlugin` constructor.
- Pack has First Aid 1.6.22 (per-body-part health; cancels every player `LivingHurtEvent` at LOWEST — ✔ verified, `docs/handoff/friendly-fire-rework.md §0.1`), ElenaiDodge, Scaling Health 1.3.42, Champions, InfernalMobs, Lycanites 2.0.8.10, Spartan Weaponry 1.6.1, switchbow, RLCombat, ItemPhysic Full, JEI 4.16.1.1012.

## Testing & deploy
- Unit-test pure logic with JUnit (formatters, aggregation, distance tiers, caches, claim boxes).
- In-game: `./gradlew runServer` + a vanilla 1.12.2 Forge client; then the user's local Dregora instance.
- Live server (owner runs it; **ask before touching**): container `minecraft` under rootless podman on host `kyuubi`; extra jars go in `~/srv/minecraft-extra/mods/` (copied into `/data/mods` at start); logs `journalctl --user -u minecraft`; rcon via `podman exec minecraft rcon-cli "<cmd>"`; spark is installed (`spark tickmonitor --threshold-tick 150`, `spark profiler start --only-ticks-over 1000 --timeout 300`).

## Conventions
- Logger: `LogManager.getLogger("dregorapatches")` (`DregoraPatches.LOGGER`).
- Config: Forge `@Config` (`PatchesConfig`), one category per module; feature toggles `<module>.enabled`.
- Lang keys under `assets/<modid>/lang/en_us.lang`, but **server-sent text must be literal** (clients don't have the lang file) — use `TextComponentString`, not `TextComponentTranslation`, for our own strings.
- Small commits per step; update the module doc's "⚠ verify" items as they're resolved.

## Open questions for the user
- Owner creates `tidefox/friendly-fire-rework` (the rename to `dregora-patches` is done).
