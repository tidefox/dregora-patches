# 03 — Kill feed (weapon follow-up line) · server-only · passive

Goal: every kill that already gets a line in chat also names **the weapon**, enchanted, renamed or plain, with a hover tooltip. We don't replace or reword anyone's message; we add one line under it:

```
Blight Xaphan Lvl 4 was killed by Crossfire!    ← Scaling Health's own line (real, from the server), untouched
  ↳ with [Longbow] · 97m · final 22.0 · 1m12s   ← ours
```
```
Bob was slain by Alice
  ↳ with [Diamond Sword] · final 18.4 · 42s
```

Decided with the owner (2026-11-08): only weapon, distance, final hit, fight time. No assists, no trick-shot tier in this line, no streaks/revenge/pet credit, no replacing vanilla's message (no `showDeathMessages` gamerule trick).

---

## 1. Reference

### Why weapons don't show today
Vanilla 1.12.2 `EntityDamageSource#getDeathMessage` only appends "using [item]" when the held item `hasDisplayName()` (anvil-renamed). Arrows (`EntityDamageSourceIndirect`) use the *shooter's* held item at death time, not the bow that fired.

### Message order
`EntityPlayerMP.onDeath` fires `LivingDeathEvent` *before* it sends the vanilla death message, so a line sent from the death event would land **above** it. The follow-up is queued and broadcast at `ServerTickEvent` END. ⚠ verify in-game that Scaling Health's Blight line (sent from its own death handler) also comes first.

## 2. Spec

### 2.1 When
| Kill | Follow-up |
|---|---|
| Player kills player | ON (`killfeed.playerKills`); skipped while gamerule `showDeathMessages` is false (no vanilla line to follow) |
| Player kills an announced mob | ON (`killfeed.announcedMobKills`) |
| Anything else | nothing |

"Announced mob" = `trick/AnnouncedMobs` (`06 §2.2`): boss flag, Blight flag (`trickshots.blights`), custom name, `trickshots.mobs`, `trickshots.nbtMarkers`. That list should match the mobs the pack itself announces (Blights via Scaling Health). Check which other kills get a pack line (⚠ verify: Champions, InfernalMobs, dragons, bosses).

Blights (✔ verified against `ScalingHealth-1.12.2-1.3.42+147.jar`, 2026-11-09): `BlightHandler.isBlight` = `entity.getEntityData().getBoolean("ScalingHealth.IsBlight")`; the `Blight {} was killed by {}` line is sent from Scaling Health's own `LivingDeathEvent` handler (`onBlightKilled`), so it lands before our tick-end follow-up. Before this fix the follow-up never fired for Blights: they were only matched through `nbtMarkers`, which shipped empty. Now a built-in rule (`trickshots.blights = true`, a cheap ForgeData read, no serialization) covers them; a new config field, so existing server cfgs pick it up with its default.

Pack line for Blights (seen on the server, 2026-11-08): `Blight <mob name> was killed by <player>!`, e.g. `Blight Xaphan Lvl 4 was killed by Crossfire!`. ⚠ verify: the `Lvl 4` is Lycanites' level in the mob's name. If Lycanites sets it as a *custom name*, the `NAMED` rule in `AnnouncedMobs` would match every Lycanites mob and give them a follow-up with no pack line above it. `/dpatch debug kill` prints `customName=`; if it's set on ordinary Lycanites mobs, add a way to turn off the `NAMED` rule.

### 2.2 Credit and weapon
- Killer = `source.getTrueSource()` if it is a player other than the victim. Otherwise no follow-up.
- Weapon: projectile with our `dregorapatches:shot` tag from the killer → the launcher that fired it (survives weapon swaps); else the killer's main hand; empty hand → `bare-handed`.
- Distance (blocks, shot origin → victim) only for tagged shots.

### 2.3 Final hit and fight time
- Mobs: `LivingDamageEvent` at LOWEST (post-armor amount).
- Players: `LivingHurtEvent` at LOW (First Aid cancels it at LOWEST, `00` observer rule 5); amount is before armor.
- Only hits whose true source is a player, and for mobs only announced ones (cheap check first, `AnnouncedMobs.cached` second).
- Fight = first player hit → death; a gap > `fightTimeoutSec` (60) starts a new fight. Fights < 1 s show no time. Final hit shown only if the killing hit landed that tick.

### 2.4 Config (`killfeed`)
`enabled, playerKills, followPackLines, announcedMobKills, announceUnannounced, announceMobs[], announceMinMaxHealth, announceNamed, petKills, lingerCreditSec, lineWaitTicks, showDistance, showFinalHit, showDuration, fightTimeoutSec`.

### 2.5 Dynamic engine (2026-11-09)
Problem (owner): kill lines from the pack showed up with no follow-up under them, because §2.1 only knew a fixed list (players, `AnnouncedMobs`) and only credited a player who dealt the blow. Ice and Fire dragons and other big mobs had no line at all.

Now the follow-up is decided by what is actually in chat:
1. **Chat tap.** `killfeed/ChatTap` keeps the plain text of every *system* line the server sent in the last few ticks (`PlayerList.sendMessage(c, true)`, `EntityPlayerMP.sendMessage`, non-action-bar `sendStatusMessage`; player chat is not seen; our own lines are skipped). Fed by two read-only HEAD injects in `mixin/vanilla` (`require = 0`); the only mixin a feature depends on (`00` observer rule 2 exception).
2. **Every player-credited death becomes a pending kill**, with the victim's names (display name, `getName`, custom name; `§` codes stripped).
3. **Tick end:** the oldest unused line sent since the death that contains one of those names (case-insensitive, ≥ 3 chars) is claimed → follow-up under it. Works for vanilla death messages, Scaling Health's Blight line, and any other mod's announcement, with no list to keep up to date. Each line serves one kill.
4. **No line within `lineWaitTicks` (2):** if the mob is *notable* and `announceUnannounced`, we send our own line plus the follow-up:
   ```
   [Fire Dragon] was slain by Alice          ← ours (gold name, entity hover)
     ↳ with [Longbow] · 97m · final 22.0 · 1m12s
   ```
   Notable = boss flag, Blight, `killfeed.announceMobs` (default `iceandfire:firedragon`, `icedragon`, `lightningdragon`, `seaserpent`; ⚠ verify the ids with `/dpatch debug kill`), `trickshots.mobs` / `nbtMarkers`, max health ≥ `announceMinMaxHealth` (0 = off), named mobs only with `announceNamed`. Anything else with no line → nothing (ordinary zombies stay silent). `killfeed.announceMobs` also counts for trick shots.
5. **Credit** (`KillCredit.of`): the player who dealt the blow; else the online owner of a tamed true source (`IEntityOwnable`, `petKills`) → `↳ Alice's [Wolf] · …`; else, only for deaths with no attacker at all (fire, bleeding, fall, poison), the player whose hit landed within `lingerCreditSec` (10) → `↳ Alice's last hit, with [Sword] · …`. Fights are now tracked for every victim a player (or pet) hits, not only announced mobs, since we only learn at death whether the pack announced it.
6. Deaths fired twice for one entity (corpse mobs) get one line.
7. Fallback: `followPackLines = false` or the hooks did not apply → §2.1 static rules as before.

`/dpatch debug kill [n]` now also reports the kill feed's decision for the next n player-credited deaths: `follow-up under: <line>`, `own line + follow-up`, or why nothing was sent.

## 3. As built (P3, 2026-11-08)
`killfeed/KillFeedListener` (events, tick-end queue), `killfeed/Fight` (pure), `killfeed/KillLine` (pure tail text), `killfeed/KillCredit`, `Fmt.duration`. `/dpatch reload` now also clears the `AnnouncedMobs` cache so new markers apply at once. Trick-shot callouts (`06`) are unchanged and still send their own line.

### Tests (in-game, need a client)
- Unnamed sword PvP kill → `↳ with [Iron Sword] · final …` under the vanilla line.
- Shoot, switch to sword, arrow kills → shows the bow and the distance.
- Blight kill → our line under Scaling Health's line, not above.
- Fists → `bare-handed`.
- `gamerule showDeathMessages false` → no follow-up for player deaths.
- Clients without the jar see everything; hover shows enchantments.
- (§2.5) Kill an Ice and Fire dragon → `[Fire Dragon] was slain by …` + follow-up, once (corpse doesn't repeat it).
- (§2.5) Wolf kills a Blight → `↳ Alice's [Wolf]` under Scaling Health's line.
- (§2.5) Set a Blight on fire, it burns to death → `↳ Alice's last hit, with [Sword]`.
- (§2.5) Ordinary zombie kill → nothing. `/dpatch debug kill 5` explains each decision.
