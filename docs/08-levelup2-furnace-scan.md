# 08 — Level Up! 2 furnace scan · Dregora Patches

Goal: remove the ~2.3 ms per tick that Level Up! 2 spends scanning every loaded tile entity, with no gameplay change.

Lives in **Dregora Patches** (`dregorapatches`, late mixin config `mixins.dregorapatches.levelup2.json`). Ships in the same jar as `07`.

---

## 1. Reference

### 1.1 Symptom (`reference/spark-live-54min-summary.txt`)
- `levelup2.skills.crafting.FurnaceEfficiencyBonus.doFurnaceTicks` = **145 s of 1,728 s busy (8.4%)** over 54 min ≈ **2.3 ms every tick**, independent of spikes.
- 4.0% inside the filter lambda `lambda$doFurnaceTicks$0` → `TileEntity.hasCapability` on every tile entity:
  - `CapabilityDispatcher.hasCapability` (vanilla/Forge TEs)
  - librarianlib `TileMod.hasCapability → AbstractSaveHandler/FieldCache.hasCapability → String.intern` (0.54% alone)
  - Mantle `TileInventory`, Cooking for Blockheads `TileCounter`, …
- The cost scales with the number of loaded tile entities (NC ruins that keep themselves loaded add to it; see `07`).

### 1.2 Source (pack jar `Level Up- 2-1.1.23-1.12.jar`; source read from BeetoGuy/LevelUp2 `master`, which matches the profile's class and lambda names)
```java
@SubscribeEvent
public void doFurnaceTicks(TickEvent.WorldTickEvent evt) {
    if (evt.phase == TickEvent.Phase.START && !evt.world.isRemote) {
        try {
            List<TileEntity> tiles = evt.world.loadedTileEntityList.stream()
                .filter(t -> t.hasCapability(PlayerCapability.MACHINE_PROCESSING, EnumFacing.UP))
                .collect(Collectors.toList());
            tiles.forEach(t -> {
                IProcessor cap = t.getCapability(PlayerCapability.MACHINE_PROCESSING, EnumFacing.UP);
                if (cap != null && cap.getPlayerFromUUID() != null) cap.extraProcessing(cap.getPlayerFromUUID());
            });
        } catch (ConcurrentModificationException e) { e.printStackTrace(); }
    }
}
```
- `MACHINE_PROCESSING` is attached via `AttachCapabilitiesEvent<TileEntity>` only when `instanceof TileEntityFurnace` (`FurnaceEfficiencyBonus`) or `instanceof TileEntityBrewingStand` (`BrewingEfficiencyBonus`). Modded furnaces that extend `TileEntityFurnace` get it too.
- `IProcessor` is public API, so another mod *could* attach it to its own tile entities.
- Effect per tick: owned furnaces (sneak-right-click with an empty hand registers an owner) get extra cook progress and bonus output based on the owner's skill. Unowned machines are skipped (`getPlayerFromUUID() == null`).

⚠ verify against the 1.1.23 jar (javap): `doFurnaceTicks` signature, the two attach handlers and their `instanceof` checks. Grep the pack's jars for `levelup2/api/IProcessor` to see whether any other mod implements it.

---

## 2. Spec

Replace the scan, keep the semantics.

| Inject | Point | Logic |
|---|---|---|
| `FurnaceEfficiencyBonus.doFurnaceTicks(WorldTickEvent)` | HEAD, cancellable, `remap = false`, `require = 0` | if `levelup2.furnaceScanFix` → run our loop, cancel |

Our loop, per server world tick (START phase, `!isRemote`):
1. Iterate `world.loadedTileEntityList` by index (it's an `ArrayList`; no stream, no intermediate list).
2. **Cheap class filter:** skip unless the TE's class is in `CANDIDATES` (a `Set<Class<?>>`, identity-hashed, cached per class with a `ClassValue`-style map).
3. For candidates, call exactly what the original does: `hasCapability(MACHINE_PROCESSING, UP)` → `getCapability` → `getPlayerFromUUID() != null` → `extraProcessing(owner)`.
4. Keep the `ConcurrentModificationException` catch (log once instead of a stack trace every time).

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
