# Mods context — everything the Dregora Patches plan touches or depends on

One entry per mod that came up in the NuclearCraft / Level Up! 2 investigation (`07`, `08`). Versions are from `pack-mods.txt` (server `mods/`) unless noted. "Patched" = we ship a mixin into it.

Server: RLCraft Dregora 1.1.2b · Minecraft 1.12.2 · Forge 14.23.5.2860 · Java 8 · host `kyuubi`, rootless podman container `minecraft`.

---

## Summary

| Mod | Version | Role in the plan | Patched? |
|---|---|---|---|
| NuclearCraft | 2.19a → 2.19d | cause of the spikes; freeze, claims, guard | **yes** (`07`) |
| Level Up! 2 | 1.1.23 | 2.3 ms/tick tile-entity scan | **yes** (`08`) |
| Minecraft / Forge | 1.12.2 / 14.23.5.2860 | chunk loading + TE tick loop | **yes**, 3 small hooks (`07`) |
| FermiumBooter | 1.4.1 | mixin loader at runtime | no (dependency) |
| MixinBooter | 11.18 | annotations + annotation processor at compile time only | no (build only) |
| MixinExtras | bundled ⚠ | `@WrapOperation` for the tick wrapper | no (dependency) |
| FermiumMixins / RLMixins / EagleMixins / SRPMixins | 1.0.10 / 1.4.6 / 1.2.4 / 2.9.2.2 | other mixin mods: conflict check | no |
| RLC_Nuclear | 1.0.1 (Dregora 1.2.0-beta-2, not on server) | may already patch NC | no (check) |
| OpenTerrainGenerator | v9.7 | generates the NC ruins | no |
| LibrarianLib | 4.22 | makes the Level Up! 2 scan expensive | no |
| spark | 1.11.140 (`spark-unforged`) | profiling / measurement | no |
| Others seen in profiles | — | context only | no |

---

## NuclearCraft — **patched** (`07`)
- **Jar:** `nuclearcraft-1.12.2-2.19a.jar`; CurseForge project **226254**. Step 1 moves the server to **2.19d** (`CF_EXCLUDE_MODS=226254`, jar via `~/srv/minecraft-extra/mods/`).
- **Source:** `github.com/tomdodd4598/NuclearCraft`, branch `1.12.2`. 2.19a = `37cc6e3`, 2.19d = `33568a8`. Between them only `nc/util/BlockFinder.java` changed among the classes we use → same mixins for both. 2.19d also ships a radiation-handler and processor auto-output fix.
- **What's in the world:** Dregora's OTG places thousands of NC blocks in ruins/bunkers: 990 `fission_port`, 85 `fission_controller_new*` (overworld scan), plus active coolers, buffers, radiation scrubbers, passive generators, processors. No player-built fission reactor exists (owner, 2026-11-08).
- **Reactor type:** the *old* fission multiblock (controller + casing box, ports, cells, coolers). `TileFissionController` (`.New`, `.Old`) extends `TileItemGenerator`.
- **Classes and members we use** (2.19a):

| Class | Used for |
|---|---|
| `nc.tile.NCTile` | duck interface target (frozen flag, claim epoch); `isAdded` (public) |
| `nc.tile.dummy.TileFissionPort` | frozen type. `update()` = `super.update(); pushEnergy();` every tick; `onAdded → findController` once per TE instance |
| `nc.tile.dummy.TileDummy` | `masterPosition` (public), `hasMaster/isMaster/getMaster` → `world.getTileEntity` |
| `nc.tile.generator.TileFissionController` | frozen type; claim anchor; public status fields `complete`, `isProcessing`, `problem`, `heat`, `currentEnergyStored`, `getMaxHeat()` |
| `nc.tile.fluid.TileActiveCooler` | frozen type; `spreadFluidToSide` → `getTileEntity(neighbor)` |
| `nc.tile.energyFluid.TileBuffer` | **not** frozen (`IBufferable` is also used by processors); neighbour pushes covered by the guard |
| `nc.tile.radiation.TileRadiationScrubber` | not frozen; `isOcclusive → getBlockState`; `invalidate → RadiationEnvironmentHandler.removeTile` (34 s / 54 min) |
| `nc.tile.generator.TilePassiveGenerator` | not frozen; `ITileEnergy.pushEnergyToSide` → guard |
| `nc.radiation.RadiationHandler.updateWorldRadiation` | world tick; iterates loaded chunks only; reads TE inventories directly; guard scope |
| `nc.config.NCConfig` | `fission_max_size` (24), `machine_update_rate` |

- **Facts relied on:** radiation only simulates loaded chunks and doesn't depend on TE ticking; a reactor needs casing on all six faces to run and heat only changes while running → freezing or the guard can't cause a meltdown.
- **Known hazard:** removing an `active_cooler` with `setblock … air` over RCON crashed the server once (crash report never pulled: `~/srv/minecraft/crash-reports/`).
- **Upstream:** maintainer is active; an issue/PR with both profile summaries is worth filing.

## Level Up! 2 — **patched** (`08`)
- **Jar:** `Level Up- 2-1.1.23-1.12.jar`.
- **Source:** `github.com/BeetoGuy/LevelUp2`. Branch `master` has `levelup2.skills.crafting.FurnaceEfficiencyBonus.doFurnaceTicks` with the stream/lambda that matches the profile frames (`lambda$doFurnaceTicks$0`). Branch `1.12-rewrite` (modVersion 1.5.8, 2020) moved this into `CraftingSkillHandler`, so it is **not** the pack's code. ⚠ confirm against the 1.1.23 jar.
- **Problem:** every world tick it streams all of `world.loadedTileEntityList` and calls `hasCapability(MACHINE_PROCESSING, UP)` on each: 145 s / 54 min (≈ 2.3 ms/tick).
- **Capability holders:** `TileEntityFurnace` and `TileEntityBrewingStand` (and subclasses), attached via `AttachCapabilitiesEvent`. `levelup2.api.IProcessor` is public API; no other pack mod is known to implement it (⚠ grep jars).
- **Fix:** class prefilter + learning scan every 200 ticks; identical gameplay.

## Minecraft 1.12.2 / Forge 14.23.5.2860 — **3 small hooks** (`07`)
- `World.updateEntities`: `@WrapOperation` around `ITickable.update()` → freeze + NC scope.
- `ChunkProviderServer.loadChunk(int, int, Runnable)`: at `ForgeChunkManager.fetchDormantChunk` (only reached when the chunk isn't loaded) → `EmptyChunk` while in NC scope.
- `ChunkProviderServer.getLoadedChunk`: skip `unloadQueued = false` while in NC scope.
- Behaviour relied on: `getBlockState`/`getTileEntity` load chunks synchronously; touching a queued chunk cancels its unload; autosave every 900 ticks queues all non-player chunks, ≤ 100 unloaded per tick before TEs tick; `getTileEntity` scans `addedTileEntityList` while TEs tick.
- `ForgeChunkManager` tickets for the op-only keep-loaded command (limits in `config/forgeChunkLoading.cfg`).
- ⚠ decompile Forge 2860 `ChunkProviderServer` before writing the guard.

## FermiumBooter — runtime mixin loader (dependency)
- **Jar:** `FermiumBooter-1.4.1.jar`; CurseForge project **968474**, file **7279112** (used by `tidefox/skinbridge` via CurseMaven `curse.maven:fermiumbooter-968474:7279112`).
- API: `fermiumbooter.FermiumRegistryAPI.enqueueMixin(boolean late, String config)`, called from our `IFMLLoadingPlugin` (constructor, retry in `injectData`). Early = vanilla targets, late = mod targets.
- Provides Mixin at runtime; we must not require MixinBooter.

## MixinBooter — build only
- `zone.rong:mixinbooter:11.18` from the CleanroomMC maven: compile-only, for the Mixin annotations and the annotation processor that writes the refmap. Never shipped, not in the pack (same as skinbridge).

## MixinExtras — `@WrapOperation` (dependency) ⚠
- Not a separate jar in `pack-mods.txt`, but the live profile shows `wrapOperation$…fermiummixins…` and `mixinextras$bridge` frames, so a copy is loaded at runtime (probably bundled with FermiumBooter/FermiumMixins). ⚠ confirm which jar provides it and its version; if it can't be relied on, fall back to `@Redirect` after checking no other mod redirects the same call.

## FermiumMixins, RLMixins, EagleMixins, SRPMixins, fermiumasm — conflict check only
- `FermiumMixins-1.0.10`, `RLMixins-1.4.6`, `EagleMixins-1.2.4`, `SRPMixins-2.9.2.2`, `fermiumasm-5.28` (probably the source of the `mirror.normalasm.*` frames in the profile, a NormalASM fork ⚠).
- Before coding: grep their mixin configs for `World.updateEntities`, `ChunkProviderServer`, and any `nc.*` or `levelup2.*` target. SRPMixins wraps `World.countEntities` and the entity spawner (seen in profile), which we don't touch.

## RLC_Nuclear — check before P1 code
- `RLC_Nuclear` v1.0.1, CurseForge project **1293578**. Added in Dregora **1.2.0-beta-2**; not on the server. Purpose unknown.
- `unzip -l RLC_Nuclear*.jar` and read its mixin config: if it targets `TileFissionPort` / `TileFissionController` / the TE tick loop, stop and reassess.

## OpenTerrainGenerator — source of the ruins (no patch)
- `OpenTerrainGenerator-1.12.2-v9.7.jar` (+ `OpenTerrainGenerator` preset folder). Places the NC ruins/bunkers. 0.18% of the live profile. We don't change worldgen; the world is pregenerated.

## LibrarianLib — why the Level Up! 2 scan is expensive (no patch)
- `librarianlib-1.12.2-4.22.jar`. `TileMod.hasCapability → AbstractSaveHandler / FieldCache.hasCapability → String.intern` (0.54% alone). Our `08` fix stops calling it every tick; LibrarianLib itself is untouched.

## spark — measurement
- `spark-unforged-1.11.140-forge.jar`. Commands over rcon: `podman exec minecraft rcon-cli "spark tickmonitor --threshold-tick 150"`, `"spark profiler start --timeout 1800"` (unfiltered) or `--only-ticks-over 200`.
- Upload hosts (`spark.lucko.me`, `bytebin.lucko.me`) aren't reachable from the Claude sandbox; reduce `.sparkprofile` files with `tools/spark_summary.py` and paste the text.

## Spawn chunks — owner-reported
- The owner says spawn chunks are suppressed on the server (mod named "Kysxis" by the owner; not in `pack-mods.txt`, probably in `~/srv/minecraft-extra/mods/`). Relevant because spawn chunks can't be what keeps the ruins loaded.

## Others seen in the profiles (context, not patched)
| Mod | Jar | Where it shows up |
|---|---|---|
| Lycanites Mobs | `lycanitesmobs-1.12.2-2.0.8.10` | entity AI + spawner (≈ 3%) |
| Fish's Undead Rising (`com.Fishmod`) | `Fish's Undead Rising-1.4.2` | raven/mimic AI, pathfinding (≈ 4.5%) |
| Ice and Fire | `Ice and Fire-2.0.9` | dragons/trolls AI |
| RLTweaker / AI Reducer (`com.charles445`) | `RLTweaker-1.12.2-0.7.5`, `AIReducer-1.12.2-0.3.0` | collision boxes, reduced AI (≈ 3.6% self time) |
| FoamFix | `foamfix-0.10.15-1.12.2` | chunk construction |
| Phosphor | `phosphor-1.12.2-0.2.6` | lighting |
| Waystones | `Waystones_1.12.2-4.1.0` | loads the target chunk on teleport (expected) |
| Cooking for Blockheads, Mantle, Rustic | — | tile entities hit by the Level Up! 2 scan |

Entities are 48% of server busy time (mob AI); out of scope for P1, a candidate for later investigation.
