# 06 — Trick-shot announcements · server-only · passive

Goal: long-range arrow kills get a callout — **only for mobs whose kills are already announce-worthy** (bosses, Blights, named mobs, your whitelist). Normal mobs: silent, so chat stays clean.

**Constraints (Warband rules, `00` §Warband rules):** observe only; vanilla/Forge API and vanilla NBT reads only; no reflection into other mods.

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

### 2.1 Projectile origin (shared `core/ProjectileOrigin`)
On arrow spawn by a player, write `warband:shot` NBT:
`{ x, y, z, tick, launcher: ItemStack, airborne: !onGround, mounted: isRiding, mount: ridingEntity name }`
Every Multishot/Splitshot arrow gets its own tag.

### 2.2 "Announced mob" (passive detection)
Mob qualifies if **any**:
1. `!victim.isNonBoss()` (vanilla boss flag — dragons, withers, modded bosses that set it)
2. `victim.hasCustomName()` (mods that name special mobs)
3. registry id ∈ `trickshot.mobs` (manual whitelist)
4. victim NBT (`writeToNBT` + `getEntityData`) contains a key ∈ `trickshot.nbtMarkers` — read-only, e.g. the marker Blights carry. Includes keys under `ForgeCaps` (where Forge serializes mod capabilities, e.g. a Champions tier), still plain vanilla NBT.

Items 3–4 are filled from `/wb debug kill` output (§2.6) — no guessing other mods' internals.

Candidates in this pack to run the debug against: Scaling Health Blights, Champions, InfernalMobs, Lycanites bosses, playerbosses, Ice and Fire dragons.

### 2.3 Qualifying shot
- Killing source is an arrow with `warband:shot`, true source = that player.
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
`/wb debug kill [n]` (op): for the next n deaths caused by a player, log registry id, class, custom name, `isNonBoss`, top-level + ForgeData + ForgeCaps NBT keys, killing source type, projectile class (Spartan bolts, switchbow arrows). Use it once on a Blight, a dragon, a boss → paste keys into config.

### 2.7 Config
`enabled, minDistance, tiers[], mobs[], nbtMarkers[], requireAnnounced (true), privateActionBar (false), scope, showFlightTime, showTags`.

---

## 3. Implementation plan

```
trick/
  TrickShotModule.java
  AnnouncedMobs.java    rules 1–4, cached per class/registry id
  TrickShotListener.java LivingDeathEvent → qualify → message
  ShotRecords.java      WorldSavedData + /trickshots
core/
  ProjectileOrigin.java ArrowLooseEvent stash + EntityJoinWorldEvent tag
  DebugKill.java        /wb debug kill
```

### Steps
1. ProjectileOrigin; verify tag on longbow, crossbow, Multishot arrows (`/data`-style dump via debug).
2. `/wb debug kill` on live server → fill `mobs` / `nbtMarkers`.
3. Distance + tiers + message on a whitelisted test mob.
4. AnnouncedMobs rules; confirm ordinary zombie stays silent.
5. Records + `/trickshots`.

### Tests
- 50m kill on zombie → silent; on whitelisted mob → callout.
- Shoot, swap to sword, arrow kills → launcher = bow.
- Arrow kill after chunk unload/reload → tag survives.
- Mounted on flying mount → "mounted on …".
- Dispenser arrow → ignored.
