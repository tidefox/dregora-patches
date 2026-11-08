# 03 — Kill feed (weapon, assists, fight stats) · server-only

Goal: every notable kill names the killer **and the weapon** (hoverable), including bow/crossbow kills, pet kills, and boss/Blight/dragon fights with assists and duration.

---

## 1. Reference

### Why weapons don't show today
Vanilla 1.12.2 `EntityDamageSource#getDeathMessage` only appends "using [item]" when the held item `hasDisplayName()` (anvil-renamed). Unnamed gear → no weapon. Arrows (`EntityDamageSourceIndirect`) use the *shooter's* held item at death time, not the bow that fired.

### Prior art
- **Heroic Death** (1.12.2, needs Mixin): JSON death messages per source.
- Bukkit DeathMessages: hoverable weapon placeholders, per-mob messages (feature target).

### APIs
| Need | API |
|---|---|
| Death hook | `LivingDeathEvent` (priority LOWEST, ignore if canceled) |
| Damage tracking | `LivingDamageEvent` (post-armor, actual amount) |
| Bow release | `ArrowLooseEvent` (gives bow stack, fires before arrow spawn) |
| Projectile tagging | `EntityJoinWorldEvent` → `EntityArrow`, store in `entity.getEntityData()` |
| Owner of pet | `IEntityOwnable#getOwner()` (vanilla, Ice and Fire dragons); Lycanites ⚠ verify it implements `IEntityOwnable`; if not, no pet credit (no reflection) |
| Boss check | `!entity.isNonBoss()` + config registry list |
| Blight / dragon / special-mob check | **Reuse `06` `AnnouncedMobs`** (vanilla boss flag, custom name, registry whitelist, NBT/ForgeCaps markers). No reflection into Scaling Health / Ice and Fire. |
| Hover | `SHOW_ITEM` (weapon), `SHOW_ENTITY` (victim: `{name, type, id}`) |

---

## 2. Spec

### 2.1 What gets announced
| Event | Default |
|---|---|
| Player death | ON — follow-up line after vanilla message |
| Player killed tracked mob (boss / Blight / dragon / whitelist) | ON |
| Player killed any mob | OFF |
| Pet/mount kill of tracked mob | ON (credited to owner) |

Scope: `kill.scope = GLOBAL | DIMENSION | RADIUS(n)`, default GLOBAL.

### 2.2 Attribution
```
trueSrc = source.getTrueSource()
if trueSrc is EntityPlayerMP            → killer = trueSrc
elif trueSrc is IEntityOwnable w/ player owner → killer = owner, via = trueSrc ("Raiko of Name")
elif null && tracked.lastPlayerHit within 5 s   → killer = that player, verb = "finished off"  (lava, fall, void)
else                                    → environment, no announce (unless player death)

weapon:
  immediate has tag dregorapatches:shot (combat/ShotTag) → its launcher stack
  immediate == killer                    → killer main hand
  else                                   → killer main hand, marked "(indirect)"
```
Projectile tagging: done by `combat/ProjectileOrigin` (built in P2, `06 §2.1`) — `ShotTag.read(immediate).launcher`. No separate `LauncherTagger`. Covers Spartan crossbows if their bolts extend `EntityArrow` (⚠ verify with `/dpatch debug kill`).

### 2.3 Fight tracking (tracked targets only)
- On `LivingDamageEvent` where victim tracked & attacker resolves to player: `fights[victimUUID].add(player, amount)`, set `start` on first hit.
- **Cost rule:** this event fires for every hit on every entity, and mob ticking is already ~48% of server time (`reference/spark-live-54min-summary.txt`). Check the cheap condition first (attacker resolves to a player), and only then `isTracked`. `isTracked` may serialize NBT (`06 §2.2` rule 4), so cache its result per entity (`WeakHashMap<Entity, Boolean>`, or entity id + world, cleared on death/unload): at most one NBT read per mob.
- Expire fight after `kill.fightTimeoutSec = 60` without damage; clear on death/unload.
- On death: final-hit amount, duration, assists = players with damage in last `kill.assistWindowSec = 15` (excluding killer), sorted by damage.

### 2.4 Message formats
Player death (follow-up under vanilla line):
```
↳ [Lightning Dragonbone Longsword] · 18.4 dmg
```
Tracked kill:
```
⚔ Name slew [Ice Dragon (Stage 4)] with [Longbow] · final 22.0 · 1m12s
   assists: Alice 340 · Bob 120
```
Option `kill.replaceVanillaPlayerDeath` → sets gamerule `showDeathMessages false` at server start and emits a full custom line instead.

Blight kills: Scaling Health already broadcasts; we only send the `↳` follow-up (`kill.blightFollowUpOnly = true`).

### 2.5 Config
`enabled, scope, radius, announcePlayerDeaths, announceBosses, announceBlights, announceDragons, announceAllMobs, whitelist, blacklist, assistWindowSec, fightTimeoutSec, replaceVanillaPlayerDeath, blightFollowUpOnly, showDamageNumbers`.

---

## 3. Implementation plan

```
kill/
  KillModule.java
  Tracked.java            isTracked(entity) → trick/AnnouncedMobs.cached (per-entity cache, passive)
  FightTracker.java       LivingDamageEvent accumulation, expiry tick
  Attribution.java        killer/via/weapon resolution
  KillAnnouncer.java      LivingDeathEvent → components → broadcast
```

### Steps
1. `/dpatch debug damage` (core) on the live pack: record source types for bow, crossbow, Ice and Fire lightning chain, Lycanites pet hits, Blight hits. Fill ⚠ items.
2. Melee attribution + hover weapon; player-death follow-up.
3. Launcher from `ShotTag` (ProjectileOrigin, P2); test longbow, Spartan crossbow, Multishot/Splitshot arrows (every arrow tagged).
4. Tracked detection via `AnnouncedMobs` (already built in P2).
5. FightTracker + assists + duration.
6. Pet credit (Raiko, dragons).
7. Config, scope. All text as literal `TextComponentString` (clients have no lang file of ours; `CLAUDE.md` Conventions).

### Tests
- Unnamed sword kill → weapon shown.
- Switch to sword after shooting, arrow kills → shows bow, not sword.
- Knock tracked mob into lava → "finished off".
- Server restart mid-fight → no NPE, fight dropped.
- Clients without jar see everything.
