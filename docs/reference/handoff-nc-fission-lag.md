# Handoff — Dregora tick spikes: NuclearCraft fission port search (2026-11-08)

## Symptom
- RLCraft Dregora (Forge 1.12.2, 14.23.5.2860) on kyuubi. MSPT median ~20 ms, but 1.1–2.6 s spikes every 45 s (900 ticks), as a 3-tick burst (~270 → ~350 → 1100–2600 ms).
- Not GC (young GC 15–40 ms, 0 old-gen collections). Not the backup timer. Not worldgen (world is pregenerated; reproduced with 2 players standing still).

## Root cause (confirmed by profile)
spark `--only-ticks-over 1000` profile, 6 slow ticks:
```
99.7% World.updateEntities (tile entities)
 93.3% nc.tile.dummy.TileFissionPort.update
  88.2%  NCTile.update → TileFissionPort.onAdded → findController
   17.1%   World.getTileEntity → Vec3i.equals
    6.0%   nc.util.BlockFinder.position
  5.1%   TileFissionPort.pushEnergy
 1.8% TileActiveCooler.update (spreadFluid)
```
- Worldgen **ruined NuclearCraft reactors** (old fission multiblock) are all over the map: scan found **990 `fission_port` + 85 `fission_controller_new`**, all in the overworld.
- `NCTile.update` calls `onAdded()` once per TileEntity instance, and `TileFissionPort.onAdded` runs `findController()`. That search scans up to `fission_max_size + 2` (default 26) blocks per axis with many block-state lookups, then calls `getTileEntity` over the shell. It never finds a valid reactor in a ruin.
- The ruin chunks get unloaded and reloaded on the 45 s autosave/unload cycle. Each reload creates fresh port TEs, so every port searches again. That is the 45 s rhythm. **Why** those chunks reload while players stand still is not yet known.
- Forge `/forge track te` flagged two ruins: **A ≈ (-1650, 11, 20)** (region `r.-4.0.mca`) and **B ≈ (-2804, 5, -2531)**.

## What was tried
- Removed NuclearCraft blocks in ruin A via RCON `setblock … air`. 205 succeeded, then the **server crashed** while removing `active_cooler -1639 6 24`. The crash report was never pulled: check `~/srv/minecraft/crash-reports/`.
- **Reverted:** restored `DregoraRL/region/r.-4.0.mca` from `~/srv/minecraft-backups/2026-11-08_0600-snap`. Verified: 289 NuclearCraft blocks are back in ruin A. The removed-state copy is at `~/r.-4.0.mca.removed`. Ruin B was never touched.

## Versions
- Server runs `nuclearcraft-1.12.2-2.19a`. The Dregora **1.2.0-beta-2.0b** manifest also pins **2.19a**, so a pack update does not change NuclearCraft.
- Beta 2 **adds `RLC_Nuclear.v1.0.1`** (CurseForge project 1293578, not in the current server). Purpose unknown. It may already patch this; check with `unzip -l RLC_Nuclear*.jar` and look for a mixin targeting `TileFissionPort`.
- Upstream latest is **2.19d** (2026-11-06). Changelog: "Improved performance of fission reactor block checking". From reading the source diff: `BlockFinder.find` now reads the block state once per position instead of up to ~11 times (one read per candidate block). The `getTileEntity` shell scan is unchanged, and the search still runs on every chunk load. So spikes should shrink (estimated, not measured), not disappear. No registry or block changes since 2.19a, so it's a low-risk drop-in.
- `TileFissionPort.java` is **identical in 2.19a and 2.19d**, so a mixin on `findController` applies to both.

## Plan (decided)
Do the two steps separately and measure after each, so each change's effect is visible.

1. **Update to 2.19d first.** It's low risk, makes the first search per port cheaper, and brings the other NuclearCraft performance fixes (radiation handler, processor auto-output). Steps:
   - set `CF_EXCLUDE_MODS=226254`
   - put the 2.19d jar in `~/srv/minecraft-extra/mods/` (COPY_MODS_SRC)
   - check that only one nuclearcraft jar ends up in `/data/mods`
   - measure with tickmonitor; this is the baseline.
2. **Then the mixin.** It removes the repeat search every 45 s, which is what actually ends the spikes. Measure again.

| | 2.19a + mixin | 2.19d alone | 2.19d + mixin |
|---|---|---|---|
| First search per port | full cost | cheaper | cheaper |
| Repeat search every 45 s | gone | still happens, just cheaper | gone |
| Other NuclearCraft fixes | no | yes | yes |

### Mixin spec (could live in the planned Dregora co-op mod)
- Inject at `TileFissionPort.findController()V`:
  - **HEAD, cancellable:** if (dim, pos) is in the miss cache, cancel. The port stays masterless.
  - **RETURN:** if `hasMaster()`, remove (dim, pos) from the cache; otherwise add it.
- Clear the cache on any `nuclearcraft:*` block place/break (`BlockEvent.EntityPlaceEvent` / `BreakEvent`), so player-built reactors still form.
- Use `remap = false` (NuclearCraft classes) and **`require = 0` on every inject**. If a future NuclearCraft version renames the method, the patch stops applying and the server still boots instead of crashing.
- Optional: persist the cache in `WorldSavedData` so even the first search after a restart is skipped.
- The sketch was written but never compiled. Check `hasMaster()` (TileDummy) and the class names against NuclearCraft source, branch `1.12.2` of tomdodd4598/NuclearCraft.
- The pack already loads mixins (FermiumBooter/MixinBooter), so no extra loader is needed.

## Other options
- **Config:** `config/nuclearcraft.cfg` → `I:fission_max_size` 24 → ~8–12. The failed-search cost scales with it. Cost: player-built reactors bigger than that won't form. Not needed if the mixin works.
- **Upstream:** a PR or issue on the NuclearCraft `1.12.2` branch with the spark profile. The maintainer is active.
- **Last resort:** remove ports/buffers/controller from ruins near play areas. **Exclude active coolers and water sources** (removing one crashed the server). Back up first.

## Tools on kyuubi
- `~/find_te.py`: parallel region-file tile-entity scanner. Run:
  `podman run --rm -v /home/tidefox/srv/minecraft/DregoraRL:/w:ro,Z -v /home/tidefox/find_te.py:/f.py:ro,Z docker.io/library/python:3.12-alpine python /f.py /w nuclearcraft [x z radius]`
- spark v1.11.140 works. Quote args through rcon-cli: `podman exec minecraft rcon-cli "spark profiler start --only-ticks-over 1000 --timeout 300"`.
- Verify a fix: `rcon-cli "spark tickmonitor --threshold-tick 150"`, then `journalctl --user -u minecraft --since -10min -o cat | grep 'Tick #'`.

## Other notes
- GC tuning is not the issue. Heap 8–10 G is fine. Consider dropping `-XX:G1HeapRegionSize=32M` from `JVM_OPTS`, since Aikar's flags already set the region size.
- Don't lower view distance (10 is needed for dragons).
