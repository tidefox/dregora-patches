# 06 — Trick-shot announcements · server-only · passive

Goal: long-range arrow kills get a callout — **only for mobs whose kills are already announce-worthy** (bosses, Blights, named mobs, your whitelist). Normal mobs: silent, so chat stays clean.

**Constraints (observer rules, `00` §Observer rules):** observe only; vanilla/Forge API and vanilla NBT reads only; no reflection into other mods.

---

## 1. Reference

| Need | API |
|---|---|
| Arrow spawn | `EntityJoinWorldEvent` → `EntityArrow`, `shootingEntity` |
| Bow stack at release | `ArrowLooseEvent` (fires before arrow spawns) |
| Tag storage | `entity.getEntityData()` (Forge persistent NBT, survives chunk unload) |
| Kill | `LivingDeathEvent`, `getImmediateSource()` = arrow, `getTrueSource()` = player |
| "Announced" check | `!isNonBoss()`, `hasCustomName()`, registry id, entity NBT keys (`writeToNBT` / `getEntityData`) |
| Records | `WorldSavedData` on overworld |

---

## 2. Spec

### 2.1 Projectile origin (shared `combat/ProjectileOrigin`)
On arrow spawn by a player, write `dregorapatches:shot` NBT:
`{ x, y, z, tick (getTotalWorldTime), shooter (UUID string), launcher: ItemStack, airborne: !onGround && !mounted, mounted: isRiding, mount: ridingEntity name }`
Every Multishot/Splitshot arrow gets its own tag (the `ArrowLooseEvent` stash is kept for the whole tick). Launcher fallback when no stash this tick: active item → held item whose registry path or class contains `bow` (covers crossbows) → main hand. Re-joins (chunk reload) keep the original tag. ⚠ verify Spartan bolts / switchbow arrows extend `EntityArrow` (`/dpatch debug kill` prints the projectile class and `[dregorapatches:shot]`).

### 2.2 "Announced mob" (passive detection)
Mob qualifies if **any**:
1. `!victim.isNonBoss()` (vanilla boss flag — dragons, withers, modded bosses that set it)
2. Scaling Health Blight: ForgeData `ScalingHealth.IsBlight` is true (`trickshots.blights`, default on; ✔ verified in the 1.3.42 jar, `03 §2.1`)
3. `victim.hasCustomName()` (mods that name special mobs)
4. registry id ∈ `trickshot.mobs` (manual whitelist)
5. victim NBT (`writeToNBT` + `getEntityData`) contains a key ∈ `trickshot.nbtMarkers` — read-only, e.g. the marker Blights carry. Includes keys under `ForgeCaps` (where Forge serializes mod capabilities, e.g. a Champions tier), still plain vanilla NBT.

Items 4–5 are filled from `/dpatch debug kill` output (§2.6) — no guessing other mods' internals.

Evaluate rules 1–4 first (cheap) and rule 5 only if they fail; rule 5 calls `writeToNBT`, a full entity serialization. Here it runs once per qualifying kill, which is fine; `03` calls the same check per hit and must cache it (`03 §2.3`).

Candidates in this pack to run the debug against: Scaling Health Blights, Champions, InfernalMobs, Lycanites bosses, playerbosses, Ice and Fire dragons.

### 2.3 Qualifying shot
- Killing source is an arrow with `dregorapatches:shot`, true source = that player.
- `distance = |origin → victim position at death|` (3D, blocks).
- `distance ≥ trickshot.minDistance (40)`.

### 2.4 Tiers & message
| Tier | Distance |
|---|---|
| Nice shot | 40 |
| Long shot | 64 |
| Sniper | 96 |
| Legendary | 128 |

```
🎯 Sniper! Name — 97m on [Ice Dragon] with [Longbow] · mounted on Raiko · 2.4s flight
   ★ new personal best (prev 71m)
```
- `[Ice Dragon]` hover SHOW_ENTITY, `[Longbow]` hover SHOW_ITEM.
- Tags: `airborne`, `mounted on X`, flight time `(deathTick − shotTick)/20`.
- Broadcast GLOBAL (it's already a rare event).
- Non-announced mobs: nothing in chat. Optional `trickshot.privateActionBar` (default OFF): shooter-only action-bar "62m" for any arrow kill ≥ minDistance.

### 2.5 Records
`WorldSavedData`: per-player best `{distance, mob, date}` + server top 10.
- `/trickshots` → top 10 (hoverable).
- `/trickshots <player>` → that player's best.
PB / server-record line appended to the callout when beaten.

### 2.6 Debug
`/dpatch debug kill [n]` (op): for the next n deaths caused by a player, log registry id, class, custom name, `isNonBoss`, top-level + ForgeData + ForgeCaps NBT keys, killing source type, projectile class (Spartan bolts, switchbow arrows). Use it once on a Blight, a dragon, a boss → paste keys into config.

### 2.7 Config
Category `trickshots` in `dregorapatches.cfg`: `enabled, minDistance, tiers[] ("Sniper=96"), mobs[], nbtMarkers[], requireAnnounced (true), privateActionBar (false), scope (GLOBAL|DIMENSION|RADIUS) + radius, showFlightTime, showTags`.

`nbtMarkers` syntax: `Key` or `a/b/c`, optionally `=value` / `!=value` (numbers compare numerically, `1b` = `1`). Each path is tried from the entity root, from `ForgeData` and from `ForgeCaps`, e.g. `ForgeCaps/<cap id>/tier!=0` for a capability that every mob carries with tier 0.

2026-11-12: with the kill feed live, the callout is folded into the kill line (`03 §2.6`): distance after the weapon, tier/mount/flight/PB in its hover, a second line only for a new server record.

### As built (P2; moved from Warband into Dregora Patches 2026-11-08)
`combat/ProjectileOrigin` + `combat/ShotTag`; `trick/AnnouncedMobs` (rules 1–3 then 4; `cached()` = per-entity `WeakHashMap` for 03), `NbtMarker`, `Tiers`, `RecordBook` (pure; PB per player + top 10 shots overall, a player may hold several), `ShotRecords` (`world/data/dregorapatches_trickshots.dat`), `TrickShotListener`, `CmdTrickShots` (non-ops allowed). Only shots that are called out (announced mobs, or any mob with `requireAnnounced = false`) are recorded. The callout uses `➶` (BMP glyph; the 🎯 emoji has no glyph in the 1.12 font). Record line: server record beats personal best; first ever shot says "first trick shot". `/dpatch debug kill` is `combat/DebugProbe` (with `debug damage`).

---

## 3. Implementation plan

```
trick/
  TrickShotModule.java
  AnnouncedMobs.java    rules 1–4, cached per class/registry id
  TrickShotListener.java LivingDeathEvent → qualify → message
  ShotRecords.java      WorldSavedData + /trickshots
combat/
  ProjectileOrigin.java ArrowLooseEvent stash + EntityJoinWorldEvent tag
  DebugProbe.java       /dpatch debug kill
```

### Steps
1. ProjectileOrigin; verify tag on longbow, crossbow, Multishot arrows (`/data`-style dump via debug).
2. `/dpatch debug kill` on live server → fill `mobs` / `nbtMarkers`.
3. Distance + tiers + message on a whitelisted test mob.
4. AnnouncedMobs rules; confirm ordinary zombie stays silent.
5. Records + `/trickshots`.

### Tests
- 50m kill on zombie → silent; on whitelisted mob → callout.
- Shoot, swap to sword, arrow kills → launcher = bow.
- Arrow kill after chunk unload/reload → tag survives.
- Mounted on flying mount → "mounted on …".
- Dispenser arrow → ignored.
