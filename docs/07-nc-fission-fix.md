# 07 — NuclearCraft ruin fix (fission port tick spikes) · Dregora Patches

Goal: end the 1.1–2.6 s spikes every 45 s caused by worldgen NuclearCraft reactor ruins, and the steady NC cost they add every tick, without changing what players see.

Lives in **Dregora Patches** (`dregorapatches`), not Warband: it's a mixin into another mod (plus two small vanilla hooks).

Approach: **freeze** worldgen reactor parts (no player reactors exist) and **guard** NC so it never loads a chunk.

Sources: `reference/handoff-nc-fission-lag.md` (investigation), `reference/spark-slowticks-summary.txt` (6 ticks > 1 s, 2026-11-07), `reference/spark-live-54min-summary.txt` (unfiltered 54 min live profile, 2026-11-05). Both summaries were produced with `tools/spark_summary.py`.

---

## 1. Reference

### 1.1 Symptom
- Forge 14.23.5.2860, NuclearCraft 2.19a, MSPT median ~21 ms, mean ~26 ms.
- Spikes every 900 ticks (autosave interval), 3-tick burst ~270 → ~350 → 1100–2600 ms. Not GC, not backups, not worldgen; reproduced with 2 players idle.
- No chunk loader in the pack; spawn chunks are suppressed. Nobody has touched the ruin structures; the world was rolled back to before the mass `setblock` removal.
- Dregora's OTG generates thousands of NC blocks (ports, controllers, coolers, buffers, scrubbers, passive generators). Scan: 990 `fission_port`, 85 `fission_controller_new` in the overworld. Hot ruins: **A ≈ (-1650, 11, 20)**, **B ≈ (-2804, 5, -2531)**.

### 1.2 Profile evidence

**Slow ticks only (6 ticks > 1 s, 13.3 s sampled):**
- 93% `TileFissionPort.update`, 88% of it `onAdded → findController`.
- 14.7% `World.getTileEntity → getPendingTileEntityAt → Vec3i.equals` and 1.7% `loadedTileEntityList.contains`: both scan lists that only grow when tile entities are **added mid-tick**, i.e. chunks loading during the tile-entity phase. This is why each burst tick gets worse.
- Disk chunk loads in those ticks come from `TileBuffer.pushStacksToSide` (`getTileEntity(neighbor)`), not from coolers.

**Live, unfiltered, 54 min (63,444 ticks):** server thread busy 1,728 s (≈ 27 ms/tick).

| Item | Time | % of busy | When |
|---|---|---|---|
| Entities (mob AI etc., out of scope) | 822 s | 48% | every tick |
| `TileFissionPort.findController` | 110 s | 6.4% | spikes — 72 autosaves → ≈ 1.5 s per burst |
| `TileFissionPort.pushEnergy` | 108 s | 6.2% | **every tick**, ≈ 1.7 ms |
| `levelup2 FurnaceEfficiencyBonus.doFurnaceTicks` | 145 s | 8.4% | every tick → see `08` |
| `TileRadiationScrubber.invalidate → RadiationEnvironmentHandler.removeTile` | 34 s | 2.0% | on TE re-add |
| other NC (radiation tick, buffers, coolers, generators) | ~40 s | ~2.3% | steady |

`findController` time here is almost entirely `getTileEntity → getPendingTileEntityAt` (the shell scan against a large pending list); `BlockFinder` is < 0.3%. 2.19d only speeds up `BlockFinder`, so don't expect step 1 alone to remove the spikes.

**Synchronous disk chunk loads on the main thread (54 min):**

| Caller | Disk read |
|---|---|
| `TileDummy.isMaster ← getMaster` (port → its controller) | 2,444 ms |
| `TileRadiationScrubber.isOcclusive` (`getBlockState`) | 1,864 ms |
| `TileFissionPort.pushPortEnergyToSide` (neighbor) | 1,524 ms |
| `TileBuffer.pushStacksToSide` (neighbor) | 1,236 ms |
| `TilePassiveGenerator → ITileEnergy.pushEnergyToSide` | 268 ms |
| `RadiationHelper.getTileInventory` (radiation world tick) | 116 ms |
| everything not NC | ~760 ms |

≈ 90% of main-thread disk loads are NC tile entities reaching into chunks that aren't loaded.

### 1.3 Source facts (NuclearCraft branch `1.12.2`; 2.19a = `37cc6e3`, 2.19d = `33568a8`)
| Code | Behavior |
|---|---|
| `NCTile.update()` | `if (!isAdded) { onAdded(); isAdded = true; }` — once per TE **instance**; each chunk load = new instance = new search |
| `TileFissionPort.onAdded()` | `findController(); super.onAdded();` (no `isRemote` check) |
| `TileFissionPort.findController()V` | 4 orientations × up to `fission_max_size + 2` per axis, then `getTileEntity` over the shell; on hit sets `masterPosition` and `return`s; every other path ends `masterPosition = null` |
| `TileFissionPort.update()` | `super.update(); pushEnergy();` — **every tick**, not gated by `machine_update_rate` |
| `TileFissionPort.pushEnergy()` | `getMaster() == null → return`; else per side `pushPortEnergyToSide` → several `getMaster()` calls, each `getTileEntity(masterPosition)` |
| `TileDummy.hasMaster()` | `masterPosition == null → false`; else `isMaster(masterPosition)` = `tClass.isInstance(world.getTileEntity(pos))` |
| `TileDummy.update()` | `findMaster()` every `updateRate` ticks; `TileFissionPort.findMaster()` is empty |
| `TileBuffer.update()` | every `machine_update_rate`: `pushStacks/pushEnergy/pushFluid` → `getTileEntity(neighbor)` ×6, no loaded check |
| `TileActiveCooler.spreadFluidToSide` | `getTileEntity(neighbor)`, no loaded check |
| `TileRadiationScrubber.isOcclusive` | `world.getBlockState(otherPos)` for other radiation sources within `searchRadius`, no loaded check |
| `RadiationHandler.updateWorldRadiation` | world tick; iterates `chunkProvider.getLoadedChunks()` only; sources = items/fluids read from each chunk's tile-entity inventories (not from TE ticking); spreading uses `getLoadedChunk` (never loads) |
| `TileFissionController.checkStructure` / `run` | structure needs casing on all six faces; any failure → `complete = 0`; processing needs `complete == 1`; `heat` changes only while processing or complete; meltdown only when `heat >= getMaxHeat()` inside `update()` |
| `IBufferable` | implemented by processors, nuclear furnace, fusion dummy, machine interface, active cooler → `buffer` is a general block, not reactor-only |
| Public fields | `NCTile.isAdded`, `TileDummy.masterPosition`, `NCConfig.fission_max_size` (24), `NCConfig.machine_update_rate` |
| 2.19a → 2.19d | `TileFissionPort`, `TileDummy`, `TileActiveCooler` identical; only `BlockFinder.java` changed → mixins apply to both |

### 1.4 Vanilla 1.12.2 / Forge 2860 behavior that matters
- Server `World.getBlockState` / `getTileEntity` → `getChunk` → `ChunkProviderServer.provideChunk` → `loadChunk(x, z, null)` → `getLoadedChunk`; if null → `ForgeChunkManager.fetchDormantChunk` → `ChunkIOExecutor.syncChunkLoad` (disk). The profile shows exactly this chain.
- `getLoadedChunk` sets `chunk.unloadQueued = false`: **touching a chunk that is queued for unload cancels the unload.**
- `World.isBlockLoaded` / `WorldServer.isChunkLoaded` → `chunkExists` → never loads, doesn't touch `unloadQueued`.
- Autosave (`saveAllChunks`, every 900 ticks) queues every loaded chunk outside the PlayerChunkMap. `ChunkProviderServer.tick` unloads ≤ 100 queued chunks per tick, before tile entities tick.
- `World.getTileEntity` scans `addedTileEntityList` linearly while `processingLoadedTiles` is true.

### 1.5 Mechanism (supported by the live profile)
```
port P (chunk A) linked to ruin controller C (chunk B)
  pushEnergy every tick → getMaster → getTileEntity(C) → B queued? un-queue it. B unloaded? load it from disk.
controller C refreshes its structure → getBlockState over the shell → keeps A, and other port chunks, loaded
buffers / coolers / scrubbers / generators → getTileEntity / getBlockState on neighbors → same effect across chunk borders
⇒ a dense ruin keeps itself loaded with no player nearby (no chunk loader needed).
autosave: queue ≥ 100 chunks → unload in batches over ~3 ticks → survivors reload/rescue neighbours
          → every reloaded chunk = new port instances → findController → pending-list scans grow → 3-tick burst.
```
The same thing happens with player-built NC setups that span several chunks: today they likely keep themselves loaded too.

### 1.6 Platform
- Pack ships **FermiumBooter 1.4.1** (+ FermiumMixins, RLMixins, MixinExtras). No MixinBooter.
- Register from an `IFMLLoadingPlugin` constructor: `FermiumRegistryAPI.enqueueMixin(boolean late, String config)` ⚠ javap the 1.4.1 jar to confirm the signature.
  - `mixins.dregorapatches.vanilla.json` — early (`late = false`), targets `ChunkProviderServer`, needs a refmap (SRG).
  - `mixins.dregorapatches.nc.json` — late, `remap = false`, no refmap.
- All configs `"required": false`; every injector `require = 0`.

---

## 2. Gameplay impact

Owner facts (2026-11-08): no player has ever built a fission reactor; Dregora has no chunk loaders and doesn't need them; NC is only used as far as progression requires.

### 2.1 Reactor freeze (step 2)
Worldgen reactor parts (fission controllers, fission ports, active coolers) don't tick unless a player has placed or right-clicked an NC block near them.
- **Nothing visible changes for ruins.** Ruin ports don't form reactors, ruin coolers only move water between ruin coolers, and ruin controllers are idle (≈ 1 s of `TileItemGenerator` time in 54 min).
- **A ruin can't overheat or melt down while frozen.** Heat only changes inside the controller's `update()`.
- **Radiation is unchanged.** The world tick reads ruin inventories and tanks directly (§2.3), whether or not the blocks tick.
- **Loot and inventories still work.** A frozen controller's GUI shows stale numbers until a right-click unfreezes it; then it ticks as normal.
- **Player builds work as in vanilla NC.** Placing any `nuclearcraft:*` block, or right-clicking one, unfreezes every reactor part within `fission_max_size + 3` blocks, permanently (saved with the world). A player building a reactor next to a ruin brings that ruin back to life, which is the same as vanilla.
- Buffers, scrubbers, generators, processors, batteries are **not** frozen: players use them. The chunk guard (step 3) handles their chunk loading.

### 2.2 Chunk guard (step 3): NC never loads a chunk or keeps one loaded
What changes:
1. **NC stops acting as an accidental chunk loader.** Ruins nobody is near unload at the next autosave and stay unloaded. Dregora has no chunk loaders, so player NC machines already stop when nobody is around; this only removes the accidental cases.
2. **Machines don't push items, fluid or energy into chunks that are unloaded or queued for unload.** That only happens at the edge of the loaded area, ≥ 10 chunks from every player. The transfer is skipped, nothing is lost: items, fluid and energy stay in the source and move once both chunks are loaded. Same as vanilla hoppers and redstone.
3. **No explosion or malfunction is possible from this.** The old fission controller only runs while its structure check passes, and that check requires casing on all six faces, top and bottom included, so every chunk a reactor overlaps contains casing. If any of those chunks reads as unloaded, the check fails (`CASING_INCOMPLETE`) → `complete = 0` → no processing, no heat change (`heat` only moves while processing or while complete). The reactor pauses, then resumes on the next check once the chunk loads. It never runs with part of its coolers unseen. (Source: `TileFissionController.checkStructure` / `run`, 2.19a.)

What doesn't change: anything within players' view distance. Player chunks are never unloaded or queued, so the guard never acts there.

### 2.3 Radiation
Unchanged wherever a player can be:
- NC simulates radiation **only in loaded chunks** (`RadiationHandler` iterates `getLoadedChunks()`; spreading picks only loaded neighbours). That's upstream behavior.
- Ruin radiation comes from the world tick **reading tile-entity inventories/tanks** in the chunk, not from those tile entities ticking. Ruin radiation therefore exists whenever the chunk is loaded, i.e. whenever a player is close enough to be exposed. Freezing doesn't touch it.
- What stops: ruins far from everyone no longer simulate (decay/spread) in the background, because they no longer keep themselves loaded. The chunk's radiation level is saved with the chunk and resumes on load, like every other unloaded chunk.
- Scrubber occlusion: positions in unloaded chunks count as empty → at most a slightly better scrubbing figure at the edge of the loaded area. Scrubbers only scrub while active (powered).

---

## 3. Spec — plan (decided)

Each step is measured on its own. Steps 2 and 3 ship in one jar behind config switches; moving between them is a config edit + restart.

### Step 1 — NuclearCraft 2.19d (ops, no code)
Owner, on the server:
- `CF_EXCLUDE_MODS=226254`
- 2.19d jar → `~/srv/minecraft-extra/mods/` (COPY_MODS_SRC)
- verify exactly one nuclearcraft jar in `/data/mods`
- `spark tickmonitor --threshold-tick 150` (15 min) → **baseline**; plus a 30 min unfiltered `spark profiler` for `tools/spark_summary.py`.

Expected: cheaper scrubber removal (radiation-handler fix). Spikes still there.

### Step 2 — Reactor freeze (on by default)
**Frozen types:** tile entities that are `instanceof` `nc.tile.generator.TileFissionController` (covers `.New`, `.Old`), `nc.tile.dummy.TileFissionPort`, `nc.tile.fluid.TileActiveCooler`. Resolved by class name at startup; a missing class just drops out of the set.

**Unfrozen zones:** per dimension, a list of `BlockPos` (`WorldSavedData` `dregorapatches_nc`). A frozen-type TE ticks normally if it lies within `fission_max_size + 3` (Chebyshev) of any zone point.
- Add a zone point on `BlockEvent.EntityPlaceEvent` (also receives multi-place) of any `nuclearcraft:*` block, and on `PlayerInteractEvent.RightClickBlock` on any `nuclearcraft:*` block (server side, real players only).
- Zone points are never removed automatically (`/dpatch nc unfreeze-clear <radius>` for the owner).

**Hook: the tick call itself**, not an NC method. NC `update()` methods chain through `super.update()` and do work after it (`TileFissionPort.update` = `super.update(); pushEnergy();`), so a HEAD inject on `NCTile.update` would miss part of the tick. Instead, wrap the `ITickable.update()` call inside `World.updateEntities` (early vanilla mixin, refmap) with MixinExtras `@WrapOperation`, which composes with other mods' wrappers instead of conflicting like `@Redirect` would:
```java
@WrapOperation(method = "updateEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ITickable;update()V"))
private void dp$tick(ITickable t, Operation<Void> original) {
    if (!(t instanceof DpNcTile) || ((TileEntity) t).getWorld().isRemote) { original.call(t); return; }
    DpNcTile d = (DpNcTile) t;                      // duck interface added to NCTile by MixinNCTile
    if (d.dp$isFrozen()) { NcStats.frozenTicks++; return; }   // cached; re-evaluated when Zones.epoch changes
    NcScope.enter(t);
    try { original.call(t); } finally { NcScope.exit(); }
}
```
`dp$isFrozen()`: false unless `cfg.nc.freezeReactorParts` and the TE is a frozen type; `Zones.covers(world, pos)` is re-checked only when `Zones.epoch` (bumped on every new zone point) differs from the TE's cached epoch. A frozen TE never runs `onAdded` (no `findController`) and never calls `pushEnergy` / `spreadFluid`.
⚠ verify: MixinExtras is available at runtime with FermiumBooter 1.4.1 (the live profile shows `wrapOperation$…fermiummixins…` frames, so some copy is loaded); check whether any pack mixin already redirects this call (`javap`/grep mixin configs for `updateEntities`).

Expected: `findController` (110 s / 54 min) and `pushEnergy` (108 s) go to ~0, including ruins near players. The port↔controller loop that keeps ruins loaded is gone. Buffers / scrubbers / generators may still load chunks → step 3.

### Step 3 — Chunk guard (off by default, turned on after step 2 is measured)
**Scope flag:** `NcScope` (static, server thread):
- Entered around each NC tile-entity tick by the same `@WrapOperation` as step 2 (try/finally, so the whole tick including code after `super.update()` is in scope). Remembers the TE class for stats.
- `RadiationHandler.updateWorldRadiation` HEAD → enter, RETURN → exit.
- Reset to 0 at `ServerTickEvent` START (safety if an exception skipped an exit).

**Guard:** two injections in `ChunkProviderServer` (early mixin, refmap), active only while `NcScope` is entered:
1. `loadChunk(IILjava/lang/Runnable;)` at the `ForgeChunkManager.fetchDormantChunk` call (reached only when the chunk isn't loaded), cancellable → return a new `EmptyChunk(world, x, z)`. `World.getTileEntity` then returns `null`, `getBlockState` returns air, and `provideChunk` doesn't try to generate. The `CallbackInfoReturnable` is only allocated on this rare path.
2. `getLoadedChunk(II)`: `@Redirect` the `PUTFIELD Chunk.unloadQueued` → skip the write while in scope. NC can still read a queued chunk this tick but no longer rescues it from unloading. No allocation.

⚠ verify both injection points against a decompiled Forge 2860 `ChunkProviderServer` (the 54 min profile shows `loadChunk → fetchDormantChunk → syncChunkLoad`). This covers every NC caller in §1.2, including interface default methods and the scrubber's `getBlockState`, without per-method patches. Unfrozen ports are covered too: their `findController` sees air in unloaded chunks and gives the same "no controller" answer a reactor with a missing wall would.

Expected: ruins away from players unload at autosave and stay unloaded → no NC frames in `/dpatch chunkloads`, no 45 s bursts.

### Fallback — port result cache (not built unless step 2 is rejected)
Cache `findController` results per `(dim, pos)` (hit → master pos, miss), invalidated by NC place/break/explosion within `fission_max_size + 3`; needs search deferral when the guard is on so the cache never stores a false miss. Superseded by the freeze because no player reactors exist.

### Config (`config/dregorapatches.cfg`)
`nc.freezeReactorParts = true`, `nc.chunkGuard = false`, `nc.logStats = false`, `levelup2.furnaceScanFix = true` (`08`).

### Commands
- `/dpatch nc stats`: frozen TEs by type and dimension, zone points, frozen ticks skipped, guard blocks by TE class, "patched: yes/no" per mixin config.
- `/dpatch nc unfreeze-clear <radius>`: remove zone points within radius of the sender (op).
- `/dpatch chunkloads <seconds>`: logs every `ChunkEvent.Load` not caused by the player chunk map (skip stacks with `PlayerChunkMapEntry` or `ChunkIOProvider`), aggregated by first frame outside `net.minecraft`/`net.minecraftforge`, with chunk coords.

### Not doing
- `fission_max_size` reduction: caps player reactors.
- Removing ruin blocks: crashed the server once (active cooler).
- Gating `pushEnergy` to `machine_update_rate`: would change player reactor output.
- Upstream: worth an issue/PR on NuclearCraft `1.12.2` with both profile summaries.

---

## 4. Implementation plan

### 4.1 Layout (package root `io.github.tidefox.dregorapatches`)
```
core/DregoraPatchesPlugin.java     IFMLLoadingPlugin → enqueueMixin(false, vanilla) + enqueueMixin(true, nc / levelup2)
core/FermiumCompat.java            isolates FermiumRegistryAPI (same pattern as skinbridge)
DregoraPatches.java                @Mod(acceptableRemoteVersions = "*", dependencies = "after:nuclearcraft;after:levelup2")
PatchesConfig.java
mixinsupport/PatchesMixinPlugin.java, MixinState.java   log + record applied mixins
nc/FrozenTypes.java                class-name → Class set
nc/Zones.java                      unfrozen zone points + epoch
nc/ZonesData.java                  WorldSavedData
nc/NcEvents.java                   place / right-click → zone point; world unload
nc/NcScope.java                    scope flag + guard stats
mixin/nc/MixinNCTile.java          implements DpNcTile (frozen-type flag, cached frozen state + epoch)
mixin/vanilla/MixinWorld.java      @WrapOperation ITickable.update() in updateEntities (freeze + scope)
mixin/nc/MixinRadiationHandler.java  updateWorldRadiation HEAD/RETURN (scope)
mixin/vanilla/MixinChunkProviderServer.java  guard
mixin/levelup2/MixinFurnaceEfficiencyBonus.java   (doc 08)
diag/ChunkLoadTracer.java
cmd/CmdDpatch.java
resources/mixins.dregorapatches.{vanilla,nc,levelup2}.json   ("required": false)
```
`MixinNCTile` adds `@Unique` fields `dp$frozenType` (computed once from `FrozenTypes`), `dp$frozen`, `dp$zoneEpoch` and implements `DpNcTile`. Freeze and scope live in `MixinWorld` because NC `update()` overrides do work after `super.update()`.

### 4.2 Steps
0. Owner runs `unzip -l RLC_Nuclear*.jar`: if it already mixes into these classes, stop and evaluate. Also list mixin configs in RLMixins 1.4.6 / FermiumMixins for NC targets.
1. Owner: NuclearCraft 2.19d + baseline (§3 step 1).
2. Build the jar (freeze + guard + tracer + stats + `08`). Test in dev (§4.3). Hand to owner.
3. Live, freeze on / guard off → 15 min tickmonitor + `/dpatch nc stats` + 30 min profile.
4. Live, guard on → 15 min tickmonitor; `/dpatch chunkloads 300` shows no NC frames.

### 4.3 Tests
- Dev world with a copied ruin: `/dpatch nc stats` shows ruin ports/coolers/controllers frozen; `/forge track te` shows them gone from the tick list cost.
- Build a fission reactor next to a frozen ruin → placing the first NC block unfreezes the area; the reactor forms, runs, and the ruin ports in range behave as in vanilla NC. Restart → still unfrozen.
- Right-click a frozen ruin controller → GUI updates after the click.
- Radiation: Geiger counter readings near a ruin with freeze on/off match.
- Guard on: teleport away from a ruin, wait one autosave → its chunks unload (`/forge tps` loaded-chunk count drops). Return → readings match guard-off.
- Reactor straddling a chunk border near a player → forms and outputs as with the guard off. Reactor straddling into an unloaded chunk (dev: force-unload) → pauses (`CASING_INCOMPLETE`), heat constant, resumes on load.
- NuclearCraft 2.19a **and** 2.19d → all mixins apply (stats show "patched: yes").
- Remove the jar → world loads normally; `data/dregorapatches_nc.dat` is ignored.
- Live: 15 min `spark tickmonitor --threshold-tick 150`, 2 players idle → no 45 s spikes; compare with the step 1 baseline.
