# 04 — Adaptive friendly fire · server-only

Goal: friendly fire stays **on** as the baseline, but scales down with crowd density, never kills, and blocks collateral (sweeps, arrows, AoE, chain lightning) so a 5–10 player fight isn't a battle royale.

---

## 1. Reference

### Prior art
- Vanilla `/scoreboard teams option <t> friendlyfire false`: all-or-nothing melee/projectile block.
- Friendly Fire (1.11–1.20): pet/mob protection only.
- FTB Teams addons / EasyParty: modern or Hytale only; binary toggles.

### Pack-specific hazards
- **Lightning Dragonbone gear** (Ice and Fire: RLCraft Edition): chain lightning, 5 hops, armor-bypassing, 0.5 s cooldown — will chain onto allies.
- Spartan Weaponry sweeps, explosions, AoE enchants, Fire Aspect / Viper on-hit effects.

### Damage pipeline (1.12.2)
| Stage | Event | Use |
|---|---|---|
| `attackEntityFrom` start | `LivingAttackEvent` (cancelable) | hard blocks — no damage, no i-frames, no knockback. **Fires twice for players** (EntityPlayer + EntityLivingBase) → handler must be idempotent |
| `damageEntity` pre-armor | `LivingHurtEvent` | — |
| post-armor/potions | `LivingDamageEvent` (amount settable) | scaling + lethal clamp on real damage |
| player left-click | `AttackEntityEvent` | records intended target |

Fire Aspect: vanilla extinguishes the target if `attackEntityFrom` returns false → blocked hits auto-extinguish.

---

## 2. Spec

### 2.1 Scope
Applies when **victim** is a player (or an ally-owned pet, see §2.6) and **attacker** resolves to a different player who is an ally (00 §2.3). Self-damage untouched.

### 2.2 Classification
| Class | Rule | Default action |
|---|---|---|
| DIRECT | immediate == attacker, victim == attacker's `AttackEntityEvent` target this tick | **scaled** |
| SPLASH | immediate == attacker, victim ≠ intended target (sweeps) | **block** |
| PROJECTILE | immediate is `IProjectile` / `EntityArrow` / `EntityThrowable` | **block** (opt: scaled) |
| EXPLOSION | `source.isExplosion()` | **block** |
| AOE_MAGIC | `source.isMagicDamage()` or type ∈ `ff.aoeTypes` (fill from debug: IaF lightning, etc.) | **block** |
| OTHER | anything else with an ally attacker | scaled |

### 2.3 Evaluation order
```
1  not ally pair / self               → pass
2  duel(attacker, victim) active      → pass (full PvP)
3  class ∈ blocked                    → cancel (LivingAttackEvent)
4  tracked boss within ff.bossLockRadius(32) of victim → cancel
5  grace: last friendly hit attacker→victim < ff.graceSec(3) → cancel
6  n = allied players within ff.densityRadius(8) of victim (excluding victim, including attacker)
   factor = curve[n]   ; 0 → cancel
7  LivingDamageEvent: amount *= factor
   lethal clamp: amount = min(amount, health + absorption − ff.lethalFloor(4.0))
   if amount ≤ 0 → cancel
   record grace timestamp
8  feedback to attacker (action bar, throttled 1 s): "Friendly fire −70% (3 allies)"
```
Steps 1–6 computed in `LivingAttackEvent`, decision stashed `pending[victimId] = {factor, tick}`, consumed in `LivingDamageEvent` same tick (both events are in the same call stack).

### 2.4 Crowd curve (default, open decision)
| n | 0–1 | 2 | 3 | 4+ |
|---|---|---|---|---|
| factor | 1.0 | 0.6 | 0.3 | 0.0 |
Config as list `ff.curve = [1.0, 1.0, 0.6, 0.3, 0.0]`, last value repeats.

### 2.5 Duels
`/ff duel <player>` → target `/ff duel accept` within 30 s → full PvP between the pair only, ends on either death, logout, or `/ff duel end`. Lethal floor still applies unless `ff.duelLethal = true`.

### 2.6 Pets & mounts
Victim `IEntityOwnable` with owner ally of attacker → default **block all** (`ff.protectPets = true`). Attacker pet hitting ally → treated as owner's PROJECTILE class.

### 2.7 Config
`enabled, densityRadius, curve, lethalFloor, graceSec, bossLockRadius, blockSplash, blockProjectile, blockExplosion, blockAoe, aoeTypes[], protectPets, feedbackActionBar, duelLethal`.
Status: `/ff status` prints current mode, curve, and nearby ally count.

---

## 3. Implementation plan

```
ff/
  FFModule.java
  IntentTracker.java     AttackEntityEvent → lastTarget[player] = {entityId, tick}
  Classifier.java        DamageSource → Class
  FFRules.java           evaluation steps 1–6 → Decision
  FFHandler.java         LivingAttackEvent (cancel/stash), LivingDamageEvent (scale/clamp/grace)
  Duels.java             requests, active pairs, cleanup
  CmdFF.java             /ff status|duel
```

### Steps
1. `/wb debug damage` session: sword hit, sweep, longbow, crossbow, lightning chain, explosion — record `damageType`, immediate/true source classes → fill `aoeTypes`.
2. Allies + pass-through + DIRECT/SPLASH classification (IntentTracker).
3. Blocked classes in LivingAttackEvent; verify no knockback/i-frames on block, Fire Aspect extinguished.
4. Density curve + LivingDamageEvent scaling.
5. Lethal clamp incl. absorption; grace window.
6. Boss lock (reuse `kill/Tracked`).
7. Duels, pets, feedback, config.

### Tests
- 2 players: sword hit → 100%; sweep onto 2nd ally → 0.
- 5 players stacked: any hit → 0.
- Ally at 3 HP, hit for 20 → no damage (floor 4).
- Lightning longsword chain through group → no ally damage, mobs still chained.
- Duel → full damage between pair only; third ally still protected.
- Spam-click same ally → grace blocks follow-ups.
