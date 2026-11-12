# 08 — Level Up! 2 furnace scan · Dregora Patches

Status: **built** (2026-11-08): `mixin/levelup2/MixinFurnaceEfficiencyBonus` + `levelup2/FurnaceScan` + `levelup2/CandidateCache` (unit-tested); applies on a real server with the 1.1.23 jar (`ci/smoke.sh`). In-game owned-furnace comparison (§3) still to do with a client. Mod context: `reference/mods-context.md`.

Goal: remove the ~2.3 ms per tick that Level Up! 2 spends scanning every loaded tile entity, with no gameplay change.

Lives in **Dregora Patches** (`dregorapatches`, late mixin config `mixins.dregorapatches.levelup2.json`, class `io.github.tidefox.dregorapatches.mixin.levelup2.MixinFurnaceEfficiencyBonus`). Ships in the same jar as `07`. Server-only like the rest of the jar.

---

## 1. Reference

### 1.1 Symptom (`reference/spark-live-54min-summary.txt`)
- `levelup2.skills.crafting.FurnaceEfficiencyBonus.doFurnaceTicks` = **145 s of 1,728 s busy (8.4%)** over 54 min ≈ **2.3 ms every tick**, independent of spikes.
- 4.0% inside the filter lambda `lambda$doFurnaceTicks$0` → `TileEntity.hasCapability` on every tile entity:
  - `CapabilityDispatcher.hasCapability` (vanilla/Forge TEs)
  - librarianlib `TileMod.hasCapability → AbstractSaveHandler/FieldCache.hasCapability → String.intern` (0.54% alone)
  - Mantle `TileInventory`, Cooking for Blockheads `TileCounter`, …
- The cost scales with the number of loaded tile entities (NC ruins that keep themselves loaded add to it; see `07`).

### 1.2 Source — verified against the 1.1.23 jar (javap, 2026-11-08)
Pack jar: server `Level Up! 2-1.1.23-1.12.jar`, client `Level Up- 2-1.1.23-1.12.jar` (same build; `mcmod.info`: modid `levelup2`, name "Level Up! Reloaded", version `1.1.23-1.12`, sha1 `e41da792…`). Reconstructed from the bytecode of `levelup2.skills.crafting.FurnaceEfficiencyBonus`:
```java
@SubscribeEvent
public void doFurnaceTicks(TickEvent.WorldTickEvent evt) {
    if (evt.world.isRemote || evt.phase != TickEvent.Phase.END || evt.side != Side.SERVER) return;
    List<IProcessor> procs = Lists.newArrayList();
    try {
        synchronized (evt.world.loadedTileEntityList) {
            procs = evt.world.loadedTileEntityList.stream()
                .filter(t -> t != null && t.hasCapability(PlayerCapability.MACHINE_PROCESSING, EnumFacing.UP))  // lambda$doFurnaceTicks$0
                .map(t -> t.getCapability(PlayerCapability.MACHINE_PROCESSING, EnumFacing.UP))                  // lambda$doFurnaceTicks$1
                .collect(Collectors.toList());
        }
    } catch (ConcurrentModificationException ignored) {}  // silently swallowed, no stack trace
    procs.forEach(this::processTick);
}
private void processTick(IProcessor p) {
    if (p != null && p.getPlayerFromUUID() != null) p.extraProcessing(p.getPlayerFromUUID());
}
```
Differences from the GitHub `master` snippet this plan was first written from: it runs in the **END** phase (not START), checks `side == SERVER`, `synchronized`s on the list, null-checks each TE, collects the capabilities (not the TEs) and swallows the CME silently. The fix below copies these semantics exactly.
- `MACHINE_PROCESSING` is attached via `AttachCapabilitiesEvent<TileEntity>` only when `instanceof TileEntityFurnace` (`FurnaceEfficiencyBonus.registerFurnaceCapability`, key `levelup:furnacemods`) or `instanceof TileEntityBrewingStand` (`BrewingEfficiencyBonus.registerStandCapability`) — **verified**. The jar's only other `addCapability` is the player capability (`CapabilityEventHandler.onPlayerEntersWorld`). Modded furnaces that extend `TileEntityFurnace` get it too. Brewing stands are processed by the same `doFurnaceTicks` loop (there is no separate brewing tick).
- `PlayerCapability.MACHINE_PROCESSING` is `public static` (non-final) `Capability<IProcessor>` — **verified**.
- `IProcessor` is public API, so another mod *could* attach it to its own tile entities.
- Effect per tick: owned furnaces (sneak-right-click with an empty hand registers an owner) get extra cook progress and bonus output based on the owner's skill. Unowned machines are skipped (`getPlayerFromUUID() == null`).

✔ `doFurnaceTicks(TickEvent.WorldTickEvent)` signature and both attach handlers verified against the jar.
⚠ still open: grep the other pack jars for `levelup2/api/IProcessor` / `MACHINE_PROCESSING` (only possible with the jars; the learning scan covers it either way).

---

## 2. Spec

Replace the scan, keep the semantics.

| Inject | Point | Logic |
|---|---|---|
| `FurnaceEfficiencyBonus.doFurnaceTicks(WorldTickEvent)` | HEAD, cancellable, `remap = false`, `require = 0` | if `levelup2.furnaceScanFix` → run our loop, cancel |

Our loop, per server world tick, under the same guard as the original (`!isRemote`, phase **END**, `side == SERVER`):
1. Iterate `world.loadedTileEntityList` by index (it's an `ArrayList`; no stream, no intermediate list).
2. **Cheap class filter:** skip unless the TE's class is in `CANDIDATES` (a `Set<Class<?>>`, identity-hashed, cached per class with a `ClassValue`-style map).
3. For candidates (non-null), call exactly what the original does: `hasCapability(MACHINE_PROCESSING, UP)` → `getCapability` → collect; then, after the scan, `getPlayerFromUUID() != null` → `extraProcessing(owner)` for each (collect first, process after, like the original, so `extraProcessing` never runs while we hold the list lock).
4. Keep the `synchronized (loadedTileEntityList)` block around the scan and the `ConcurrentModificationException` catch (the original swallows it silently; we count it in stats).

**`CANDIDATES`:**
- Seeded with every loaded class assignable to `TileEntityFurnace` or `TileEntityBrewingStand` (that is what the attach handlers check).
- **Learning scan:** every 200 ticks (10 s), run the original filter (`hasCapability` on every TE) once and add any class that has the capability. That keeps the patch correct if some mod implements `IProcessor` on its own tile entities. Cost: 1/200 of today.

Read `PlayerCapability.MACHINE_PROCESSING` directly (public static field; NC-style hard dependency → only load the mixin config when `Loader.isModLoaded("levelup2")`, or let `require = 0` + `"required": false` handle a missing class).

### Gameplay impact
None. The same machines get the same bonus on the same ticks. The only theoretical difference: a modded tile entity class that implements `IProcessor` (none known) and is first seen between learning scans gets its bonus up to 10 s late, once per server run.

### Config
`levelup2.furnaceScanFix = true`.

### Stats
`/dpatch levelup2 stats` — candidate classes, candidates per tick, owned machines processed per tick, time per tick (moving average).

---

## 3. Tests
- Dev: place a furnace, sneak-right-click to own it, smelt with furnace-speed / furnace-bonus skill levels → same speed-up and doubling rate with the fix on and off (compare 200 smelts).
- Brewing stand owned + brewing skill → same behavior on/off.
- Unowned furnace → no bonus, on and off.
- Live: 30 min unfiltered `spark profiler` → `tools/spark_summary.py`; `doFurnaceTicks` should drop from ~4.5% of total to < 0.1%.

## 4. Live result (2026-11-08, 5–6 players)
`/dpatch levelup2 stats` at 20:00 / 20:30: fix on, 3 candidate classes (0 learned), 27.3 / 20.7 candidates per world tick, **0.137 / 0.090 ms per world tick** (was ≈ 2.3 ms per tick, §1.1), 0 CMEs. Spark (30 min): `doFurnaceTicks` → `FurnaceScan.tick` = **0.44 % of total, 7.8 s / 35,776 ticks = 0.22 ms per server tick** (two worlds × ~0.11 ms), was 145 s / 54 min = 8.4 % of busy ≈ 2.3 ms. ≈ 10× cheaper; the §3 target (< 0.1 %) is missed. 6.7 s of the 7.8 s is `CandidateCache.isCandidate` — i.e. walking `loadedTileEntityList` and reading each TE's class (cache misses on scattered objects), not the map lookup itself. Going lower needs no per-tick walk at all (track furnaces/brewing stands on TE add/remove); not worth the risk for 0.2 ms unless asked.

## 5. Reuse the candidates while the list is unchanged (2026-11-12)
The 54 min profile (`WSIKZHCREG`, after exploring) puts `FurnaceScan.tick` at **2.31 % = 74 s ≈ 1.2 ms per tick**, of which 2.04 % is `CandidateCache.isCandidate`. The walk is the cost, and it grows with `loadedTileEntityList` (ruins kept loaded; see `07 §4.8`).
- `FurnaceScan` keeps, per dimension, the candidate tile entities found on the last walk and the list's structural version (`ListVersion` = `ArrayList.modCount` by reflection). The next tick reuses them unless the list was added to or removed from. If another mod swapped the list type, the version is unknown → walk every tick, as before.
- Same TEs in the same order → same `hasCapability`/`getCapability` calls → same gameplay. Learning scans (every 200 calls) still walk everything and drop the snapshot.
- Snapshots of unloaded/replaced worlds are dropped each tick (`DimensionManager.getWorld(dim) != snapshot.world`), so they don't pin a world.
- Stats: `full list walks` next to `calls`. A low ratio = reuse is working.
- ⚠ verify live: `/dpatch levelup2 stats` at base (walks ≪ calls) and spark `FurnaceScan.tick` < 0.1 %.
