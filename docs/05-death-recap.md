# 05 — Death recap · server-only · passive

Goal: when you die, **you alone** get a breakdown of the last 10 s of damage — who/what hit you, how hard, how much armor ate — so RLCraft deaths stop being mysteries. Zero chat clutter for others.

**Constraints (Warband rules, `00` §Warband rules):** observe only. No event is canceled or modified, no other mod's classes are touched, nothing changes gameplay.

---

## 1. Reference

### Damage pipeline we observe (1.12.2)
| Event | What it tells us | Priority |
|---|---|---|
| `LivingHurtEvent` | amount **before** armor/potions | **LOW**, `receiveCanceled = false` (First Aid, below) |
| `LivingDamageEvent` | amount **after** armor, potions **and absorption** (vanilla subtracts absorption before firing it) = HP loss | LOWEST, `receiveCanceled = false` |
| `LivingHealEvent` | healing (optional net view) | LOWEST |
| `LivingDeathEvent` | death trigger | LOWEST |
| `PlayerEvent.PlayerRespawnEvent` | safe moment to deliver (chat visible) | default |

LOWEST priority = we see the final numbers after every other mod has adjusted them. Both hurt + damage fire in the same call stack, so a per-entity stash pairs them. Absorption used by a hit = absorption at hurt − absorption at damage.

### Pack interactions (from mods.txt)
- **First Aid 1.6.22** replaces the player health model: damage is split across body parts and a player can die at a head/torso 0 while total health is above 0. ✔ Verified against the jar (`ichttt.mods.firstaid.common.EventHandler.onLivingHurt`, `@SubscribeEvent(priority = LOWEST)`): for every non-fake player it applies the hit to body parts and then **cancels the `LivingHurtEvent`**. So for players `LivingDamageEvent` never fires, and a LOWEST listener with `receiveCanceled = false` may never see the hit (same-priority order = registration order). As built: the hurt is read at **`LOW`** (after every normal-priority mod, before First Aid); `final` comes from `LivingDamageEvent` when it fires, else from the change in health + absorption at the end of the tick (or at death, or at the next hurt, whichever is first). A hit where nothing changed is dropped (a later listener canceled it). At death with no change yet (First Aid head/torso kill), `final = min(raw, health)`, marked `(est.)`. Killing blow = last hit by order. First Aid also cancels `LivingHealEvent` at `LOW`, so `showHealing` (LOWEST) records nothing for players while First Aid is installed. No First Aid API calls (Warband rule 2). ⚠ verify on the live pack with `/wb debug damage`: does vanilla health move by the end of the tick under First Aid (the delta path)?
- **ElenaiDodge**: a dodge cancels the attack → we never receive it → correctly absent from the recap.

### DamageSource fields (vanilla only)
`getDamageType()`, `getTrueSource()`, `getImmediateSource()`, `isProjectile()`, `isExplosion()`, `isFireDamage()`, `isMagicDamage()`, `isUnblockable()` (armor bypass), `isDamageAbsolute()`.

---

## 2. Spec

### 2.1 Recording
Per online player, ring buffer of the last **20** hits within `recap.windowSec = 10`:
```
Hit { tick, damageType, attackerName, attackerRegistryId?, weapon: ItemStack?,
      raw (pre-armor), final (post-armor), healthAfter, flags[PROJ,EXPL,FIRE,MAGIC,BYPASS] }
```
- `attackerName`: true source display name (mob name incl. custom name, or player name); none → environment ("Fall", "Lava", "Wither effect"…).
- `weapon`: if true source is a player → launcher tag on projectile (`core/ProjectileOrigin`) else their main hand. If a mob → its main hand if non-empty (skeleton bow etc.).
- Optional `Heal { tick, amount }` when `recap.showHealing = true`.
- Buffer cleared on respawn, logout, dimension change.

### 2.2 Delivery
| When | To | Default |
|---|---|---|
| Respawn | dead player only | ON (death screen may hide chat) |
| `/recap` | caller, last death | always |
| `[share]` click → `/recap share` | everyone, once per death | ON (opt-in by click) |

### 2.3 Message
Collapsed (one line + one per source, max 5 sources):
```
☠ Death recap · 46.2 taken in 6.8 s · armor blocked 18.1
  Wyvern ×3 ........ 28.0   (armor −12.4)
  Poison ×4 ........  6.2   bypass
  ✖ Skeleton [Bow] .. 12.0   killing blow
  [timeline] [share]
```
- `[timeline]` hover: full ordered list `-6.8s Wyvern 9.5→6.1 (HP 31.4)`…
- Each attacker hover: SHOW_ENTITY (or type for env); weapon hover: SHOW_ITEM.
- Killing blow = last `Hit`; also reports overkill (`taken − (healthBefore + absorptionBefore)`).
- Absorption shown separately if it soaked damage.

### 2.4 Config
`enabled, windowSec, maxHits, maxSources, deliverOn (RESPAWN|DEATH|BOTH), allowShare, showHealing, showTimeline`.

---

## 3. Implementation plan

```
recap/
  RecapModule.java
  HitRecorder.java      LivingHurtEvent (stash raw) + LivingDamageEvent (finalize Hit) + LivingHealEvent
  RecapStore.java       per-UUID ring buffer, lastDeath snapshot
  RecapFormatter.java   aggregation + ITextComponent build (hover/click)
  CmdRecap.java         /recap, /recap share
core/
  ProjectileOrigin.java shared with 06 and 03 (launcher + origin tag on arrows)
```

### As built (P2)
`recap/`: `HitRecorder` (above), `Hit` (plain data; hovers kept as NBT strings), `RecapStore` (ring buffer + last death, in memory), `RecapSummary` (pure aggregation, unit-tested), `RecapFormatter`, `RecapModule` (death snapshot, respawn delivery, logout/dimension clear), `CmdRecap`. Sources are grouped by name; the weapon shown is the killing blow's (else the last one seen); the killing blow's source is always listed even past `maxSources`, the rest sum into `+N more`. Vanilla poison ticks are `magic` damage; with Poison active they're labelled "Poison". No damage in the window → one line with the death cause. Header shows `armor blocked` (raw − taken), `absorption`, `healed` when non-zero. Rich message falls back to a hover-less copy if its JSON exceeds 30000 chars. `/recap` and `/recap share` are usable by non-ops (`checkPermission` overridden).

### Steps
1. HitRecorder pairing hurt→damage; log to console, verify numbers vs health bar.
2. Snapshot on LivingDeathEvent; deliver on respawn.
3. Aggregation + formatter + hovers.
4. `/recap`, share click, config.

### Tests
- Fall death → environment line, no attacker.
- Death by poison tick after mob hit → both appear, poison flagged bypass.
- Mob with custom weapon → weapon hover.
- Another mod cancels a hit (e.g. dodge) → not recorded (we don't receive canceled).
- Death screen → recap appears after respawn, not lost.
- Player logs out dead → no NPE on next login.
