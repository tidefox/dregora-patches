# 07 — NuclearCraft ruin fix (fission port tick spikes) · Dregora Patches

Goal: end the 1.1–2.6 s spikes every 45 s caused by worldgen NuclearCraft reactor ruins, and the steady NC cost they add every tick, without changing what players see.

Lives in **Dregora Patches** (`dregorapatches`), not Warband: it's a mixin into another mod (plus two small vanilla hooks).

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

### 2.1 Result cache (step 2)
None visible. A port gets the same answer it would have computed; it just doesn't recompute it every time its chunk reloads. Player edits near a port (place/break any NC block) drop the cached answer so the next load searches again.

### 2.2 Chunk guard (step 3) — "NC never loads a chunk or keeps one loaded"
What changes:
1. **NC setups stop acting as accidental chunk loaders.** Ruins nobody is near unload at the next autosave and stay unloaded — the same rule as every other machine in the pack. A player's multi-chunk NC build also stops running when every player leaves its area (it probably keeps itself loaded today). ⚠ ask the owner whether anyone relies on that.
2. **Machines don't push into chunks that are unloaded or queued for unload.** Only happens at the edge of the loaded area, i.e. ≥ 10 chunks from every player. Same as vanilla redstone, hoppers and most modded pipes.
3. **A reactor whose structure crosses into an unloaded chunk reads as incomplete** until that chunk loads, then forms on the next check. Again only far from players.

What doesn't change: anything within players' view distance. Player chunks are never unloaded or queued, so the guard never triggers there.

### 2.3 Radiation
Radiation is unchanged wherever a player can be:
- NC simulates radiation **only in loaded chunks** (`RadiationHandler` iterates `getLoadedChunks()`; spreading picks only loaded neighbours). That's upstream behavior, not ours.
- Ruin radiation comes from the world tick **reading the inventories/tanks** of tile entities in the chunk, not from those tile entities ticking. So ruin radiation exists whenever the ruin chunk is loaded, i.e. whenever a player is close enough to be exposed.
- What stops: ruins far from everyone no longer "simulate" (decay/spread) in the background, because they no longer keep themselves loaded. The chunk's radiation level is saved with the chunk and resumes on load. That's how every other unloaded chunk already behaves.
- Scrubber occlusion: positions in unloaded chunks count as empty → at most a slightly better scrubbing figure at the edge of the loaded area. Ruin scrubbers only scrub when active (`isActive`), which needs power.

### 2.4 Freeze worldgen NC until a player interacts (step 4, optional)
Idea: worldgen NC tile entities skip `update()` until a player interacts near them.
- Radiation is **not** affected (see 2.3: it reads inventories in the world tick, not via `update()`).
- Ruins do nothing visible while ticking (ports rarely form a reactor; buffers/coolers shuffle between ruin blocks; generators push power into nothing).
- Extra gain over step 3: ruins **inside** players' view distance stop costing too. After step 3 that's the only NC ruin cost left.
- Costs: there is no reliable "worldgen" marker. We'd keep a persistent set of positions players touched (place/break/right-click within a radius, including non-NC blocks such as a cable placed next to a ruin buffer). NC machines players built before the patch would freeze until someone interacts with them.
- Decision: **only if** tickmonitor still shows ruin cost after step 3 **and** the owner confirms which NC builds exist on the server.

---

## 3. Spec — plan (decided)

Each step is measured on its own so its effect is visible. Everything from step 2 and 3 ships in one jar behind config switches, so the owner moves between steps with a config edit + restart.

### Step 1 — NuclearCraft 2.19d (ops, no code)
Owner, on the server:
- `CF_EXCLUDE_MODS=226254`
- 2.19d jar → `~/srv/minecraft-extra/mods/` (COPY_MODS_SRC)
- verify exactly one nuclearcraft jar in `/data/mods`
- `spark tickmonitor --threshold-tick 150` (15 min) → **baseline**; optionally a 30 min unfiltered `spark profiler` for `tools/spark_summary.py`.

Expected: smaller first-search cost, likely cheaper scrubber removal (radiation-handler fix). Spikes still there.

### Step 2 — Result cache (on by default)
Target `nc.tile.dummy.TileFissionPort`, `remap = false`, `require = 0`.

| Inject | Point | Logic |
|---|---|---|
| `findController()V` | HEAD, cancellable | cache has `MISS` → `masterPosition = null`, cancel. Cache has `HIT(m)` and `isMaster(m)` → `masterPosition = m`, cancel. Else run the search. |
| `findController()V` | RETURN | `hasMaster()` → store `HIT(masterPosition)`; else store `MISS` |

Key `(dimension, pos.toLong())`; value `long` master pos or a `MISS` sentinel (`Long.MIN_VALUE`).

**Invalidation:** `BlockEvent.EntityPlaceEvent` (also receives multi-place) / `BlockEvent.BreakEvent` / `ExplosionEvent.Detonate` where the block's registry namespace is `nuclearcraft` → drop entries within `fission_max_size + 3` (Chebyshev) in that dimension. Clear a dimension on `WorldEvent.Unload`.
Accepted gap: NC blocks changed by machines/other mods don't fire these events. Ruins are never edited (owner confirmed); player reactors are placed by players.

**Persistence (step 2b, off):** `WorldSavedData` per dimension. Only if post-restart spikes matter.

Expected: `findController` cost gone (each port searches once per server run). The ruins still reload every 45 s, so some burst cost remains (disk loads, TE re-add, scrubber removal).

### Step 3 — Chunk guard + search deferral (one switch, off by default)
**Scope flag** — `NcScope` (static, server thread):
- `NCTile.update()` HEAD → enter (only when `!world.isRemote`), RETURN → exit. Remember the TE class for stats.
- `RadiationHandler.updateWorldRadiation` HEAD → enter, RETURN → exit.
- Reset to 0 at `ServerTickEvent` START (safety if an exception skipped an exit).

**Guard** — two injections in `ChunkProviderServer` (early mixin, refmap), active only while `NcScope` is entered:
1. `loadChunk(IILjava/lang/Runnable;)` at the `ForgeChunkManager.fetchDormantChunk` call (reached only when the chunk isn't loaded), cancellable → return a new `EmptyChunk(world, x, z)`. `World.getTileEntity` then returns `null`, `getBlockState` returns air, and `provideChunk` doesn't try to generate. The `CallbackInfoReturnable` is only allocated on this rare path.
2. `getLoadedChunk(II)` `@Redirect` on the `PUTFIELD Chunk.unloadQueued` → skip the write while in scope. NC can still read a queued chunk this tick but no longer rescues it from unloading. No allocation.

⚠ verify both injection points against a decompiled Forge 2860 `ChunkProviderServer` (the 54 min profile shows `loadChunk → fetchDormantChunk → syncChunkLoad`). Covers every NC caller in §1.2, including the interface default methods and the scrubber's `getBlockState`, without per-method patches.

**Deferral** (same switch, required with the guard so the cache never stores a false `MISS`):
- `TileFissionPort.onAdded()` HEAD, cancellable: if any chunk overlapping the ±(`fission_max_size` + 2) box is not loaded or is `unloadQueued` → cancel, set `dp$deferred = true`.
- `NCTile.update()` RETURN: if `dp$deferred` → `isAdded = false; dp$deferred = false` so `onAdded` retries. Check at most every 20 ticks per port (counter field).

Expected: ruins away from players unload at autosave and stay unloaded → bursts gone, `pushEnergy` per-tick cost drops to ruins near players.

### Step 4 — Freeze worldgen NC (not planned; see §2.4)

### Config (`config/dregorapatches.cfg`)
`nc.resultCache = true`, `nc.persistCache = false`, `nc.chunkGuard = false`, `nc.logStats = false`.

### Commands
- `/dpatch nc stats` — cache size per dim (hits / misses); searches run / skipped / deferred since start; guard blocks by TE class; "patched: yes/no" per mixin config.
- `/dpatch chunkloads <seconds>` — logs every `ChunkEvent.Load` not caused by the player chunk map (skip stacks with `PlayerChunkMapEntry` or `ChunkIOProvider`), aggregated by first frame outside `net.minecraft`/`net.minecraftforge`, with chunk coords. Confirms the guard: after step 3 no NC frames should appear.

### Not doing
- `fission_max_size` reduction — caps player reactors.
- Removing ruin blocks — crashed the server once (active cooler).
- Gating `pushEnergy` to `machine_update_rate` — would change player reactor output.
- Upstream: worth an issue/PR on NuclearCraft `1.12.2` with both profile summaries.

---

## 4. Implementation plan

### 4.1 Layout
```
dregorapatches/
  DregoraPatchesPlugin.java    IFMLLoadingPlugin → enqueueMixin(false, vanilla) + enqueueMixin(true, nc / levelup2)
  DregoraPatches.java          @Mod(acceptableRemoteVersions = "*", dependencies = "after:nuclearcraft;after:levelup2")
  PatchesConfig.java
  nc/PortCache.java            result cache
  nc/NcInvalidation.java       place / break / explosion / world unload
  nc/NcScope.java              scope flag + guard stats
  nc/PortCacheData.java        WorldSavedData (step 2b)
  mixin/nc/MixinTileFissionPort.java   findController HEAD/RETURN, onAdded deferral
  mixin/nc/MixinNCTile.java            update HEAD/RETURN (scope, deferral retry)
  mixin/nc/MixinRadiationHandler.java  updateWorldRadiation HEAD/RETURN (scope)
  mixin/vanilla/MixinChunkProviderServer.java  guard
  mixin/levelup2/MixinFurnaceEfficiencyBonus.java   (doc 08)
  diag/ChunkLoadTracer.java
  cmd/CmdDpatch.java
resources/mixins.dregorapatches.{vanilla,nc,levelup2}.json   ("required": false)
```

### 4.2 Code sketch (step 2)
```java
@Mixin(value = TileFissionPort.class, remap = false)
public abstract class MixinTileFissionPort {

    @Inject(method = "findController()V", at = @At("HEAD"), cancellable = true, require = 0)
    private void dp$useCache(CallbackInfo ci) {
        TileEntity te = (TileEntity) (Object) this;
        World w = te.getWorld();
        if (w == null || w.isRemote || !PatchesConfig.nc.resultCache) return;
        long v = PortCache.get(w, te.getPos());
        if (v == PortCache.ABSENT) return;
        TileDummy<?> d = (TileDummy<?>) (Object) this;
        if (v == PortCache.MISS) { d.masterPosition = null; PortCache.skipped++; ci.cancel(); return; }
        BlockPos m = BlockPos.fromLong(v);
        if (d.isMaster(m)) { d.masterPosition = m; PortCache.skipped++; ci.cancel(); }
    }

    @Inject(method = "findController()V", at = @At("RETURN"), require = 0)
    private void dp$record(CallbackInfo ci) {
        TileEntity te = (TileEntity) (Object) this;
        World w = te.getWorld();
        if (w == null || w.isRemote || !PatchesConfig.nc.resultCache) return;
        PortCache.searched++;
        TileDummy<?> d = (TileDummy<?>) (Object) this;
        PortCache.put(w, te.getPos(), d.hasMaster() ? d.masterPosition.toLong() : PortCache.MISS);
    }
}
```
A cancelled HEAD returns immediately; RETURN injectors only run on the original method's return paths.

`PortCache`: `Int2ObjectOpenHashMap<Long2LongOpenHashMap>` with `defaultReturnValue(ABSENT)`. Minecraft 1.12 bundles fastutil 7.x: use `LongIterator`/`ObjectIterator` + `remove()` for `invalidateNear`, not `removeIf` with a primitive lambda.

⚠ verify:
- `BlockEvent.EntityPlaceEvent` exists in Forge 2860 (older builds: `PlaceEvent`); subscribing to the parent also receives the multi-place subclass.
- `ResourceLocation#getNamespace` vs `getResourceDomain` (mappings).
- Referencing `NCConfig` from `NcInvalidation` makes NC a hard class dependency → register only if `Loader.isModLoaded("nuclearcraft")`.

### 4.3 Steps
0. Owner runs `unzip -l RLC_Nuclear*.jar` — if it already mixes into `TileFissionPort`, stop and evaluate. Also list mixin configs in RLMixins 1.4.6 / FermiumMixins for NC targets.
1. Owner: NuclearCraft 2.19d + baseline (§3 step 1).
2. Build the jar (cache + guard + deferral + tracer + stats + `08`). Test in dev (§4.4). Hand to owner.
3. Live, cache on / guard off → 15 min tickmonitor + `/dpatch nc stats`.
4. Live, guard on → 15 min tickmonitor; `/dpatch chunkloads 300` shows no NC frames.
5. 2b persistence / step 4 freeze only if numbers call for it.

### 4.4 Tests
- Dev world with a copied ruin: `/dpatch nc stats` shows each port searched once; reload chunk → `skipped` increments.
- Build a fission reactor next to a cached ruin port → port links (invalidation works). Break the controller → reactor unforms as in vanilla NC.
- Guard on: teleport away from a ruin, wait one autosave → its chunks unload (`/forge tps` loaded-chunk count drops; `/forge track te` shows no ruin TEs). Return → ruin TEs tick, radiation readings near the ruin match guard-off.
- Reactor straddling a chunk border near a player → forms and outputs as with the guard off.
- NuclearCraft 2.19a **and** 2.19d → all mixins apply (stats show "patched: yes").
- Remove the jar → world loads normally (nothing written unless 2b on).
- Live: 15 min `spark tickmonitor --threshold-tick 150`, 2 players idle → no 45 s spikes; compare with the step 1 baseline.
