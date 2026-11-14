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
Superseded by §2.6: `enabled, playerKills, announceUnannounced, announceMobs[], announceMinMaxHealth (150), announceNamed, championsMinTier (3), infernalMinModifiers (6), dragonMobs[], dragonMinStage (3), petKills, lingerCreditSec, lineWaitTicks, streakSec (10), minFightSec (5), fightTimeoutSec`. Existing server cfgs keep their old values (e.g. `announceMinMaxHealth = 0`, the old `announceMobs`); set them by hand.

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

### 2.6 Compact one-line feed (decided and built 2026-11-12)
Problem (owner, screenshot of a Blight Defiled Slime fight): 21 lines for one slime chain. Every kill got a `↳ with [XP Printer 3000] · final …` line (same weapon each time); a pack line that already said `using [XP Printer 3000]` got the weapon again; every split slime was announced; Blight split slimes got **our own** gold line while plain ones took Scaling Health's line (matching bug, see *Verified* below). Long lines also wrap at default chat width (~50 chars).

Decisions:
1. **One line per kill, vanilla wording.** Replaces §2.5's "pack line + `↳` follow-up". We **replace** the matched pack/vanilla line with ours (owner lifted observer rule 1 for the kill feed only, `00` rule 1): `{mob} was slain by {killer} using 🗡` with vanilla's verb per damage type (`death.attack.*`: `slain by`, `shot by`, `blown up by`, `killed by … using magic`, `burnt to a crisp whilst fighting`, `doomed to fall by`; pets: `slain by Alice's Wolf`). Scaling Health's `Blight X was killed by Y!` becomes `Blight X was slain by Y using 🗡`. Names keep vanilla's entity hover.
2. **Weapon always, shown as an icon.** `🗡` = Quark's `RenderItemsInChat` (client, in the pack): it draws the item of any chat piece whose text starts with three spaces and has a `SHOW_ITEM` hover (vanilla's `ItemStack.getTextComponent` gets the spaces from Quark's ASM hook; read from strings in Quark r1.6-179's class, ⚠ verify in-game). So `Chat.item` gets a `"   "` prefix inside the hover piece. Clients without Quark (or with the feature off) see blanks → the weapon name and tooltip stay in the hover. Vanilla only shows `using [X]` for renamed items and, for arrows, the item in hand at death; we always show the launcher (§2.2).
3. **Weapon once.** If the pack line already contains a `SHOW_ITEM` piece (`using [X]`), keep that and add no weapon.
4. **One short extra at most** after the weapon: trick-shot distance (`· 97m`, `06`) if it qualifies, else fight time if ≥ 5 s. Everything else goes to hover: final hit, hits, fight time, trick-shot tier/mount/flight/PB. The separate trick-shot callout goes away; only a **new server record** gets a second line.
5. **Streaks collapse.** Same killer + same base mob name (without `Blight` / level / badges) within 10 s: the first kill gets its line, later ones are held and summed in one gray line when the window ends: `  …and 11 more Defiled Slimes (4 Blight) in 18s`. Bosses, named mobs, PvP and trick shots always get their own line.
6. **Fallback.** If anything fails after we held back a pack line, the original is sent unchanged. Lines we can't match are never touched.
7. **Badges** (mod stats from vanilla NBT, no reflection, `00` rule 2), in front of the mob name, details in the mob hover; also decide what's notable (elites only):

| Mod | Badge | Notable from | Source (⚠ verify keys with `/dpatch debug kill`) |
|---|---|---|---|
| Champions | `★`…`★★★★★` tier (pack has 5) + name | tier ≥ 3 | `ForgeCaps/champions:championship/tier` ✔ |
| InfernalMobs | `Elite` / `Ultra` / `Infernal` + modifiers | Ultra (6+ mods) | `ForgeData/InfernalMobsMod` ✔ |
| Ice and Fire | `Stage N` dragon | stage ≥ 3 | `AgeTicks` → stage ✔ |
| SRParasites | class (`Ancient` …) | `anc_*`, `overseer`, `warden`, `marauder`, `monarch`, `beckon_siv`/`sv`, `dispatcher_siv` (owner, 2026-11-12) | registry id ✔ |
| Lycanites | level, rare subspecies | ⚠ | NBT ⚠ |
| Scaling Health | `Blight` (already in its name) | always | done |

Mods we forget: boss flag (vanilla `isNonBoss`) + `announceMinMaxHealth = 150` catch elites from unlisted mods (Defiled Lands, BattleTowers, playerbosses, Bloodmoon, DregoraRL, …). Config: `nbtMarkers` gets `>=`/`<=` and a `label=rule` badge list; dragon stage and Infernal rank are built-in (computed, not stored).

Target, the screenshot's fight:
```
Blight Defiled Slime was slain by JCRVIII using 🗡
  …and 11 more Defiled Slimes (4 Blight) in 18s
Lancelot Dreamcatcher Defiled Slime was slain by JCRVIII using 🗡[XP Printer 3000]
```
Other examples:
```
Blight Xaphan Lv4 was shot by Crossfire using 🗡 · 97m
Stage 4 Fire Dragon was shot by Bob using 🗡 · 112m
  ★ new server record (prev 98m by Crossfire)
★★★ Zombie was slain by Alice using 🗡 · 42s
Bob was slain by Alice using 🗡 · 8s
```
Known costs: held-back lines arrive a few seconds late during a streak; long names still wrap.

#### Verified against the server pack (2026-11-12, `dregora-1.1.2b-fixed.zip`, decompiled with CFR)
- **Quark r1.6-179 `RenderItemsInChat`** (`quark.cfg` `Render items in chat=true`): Quark ASM-patches `ItemStack.getTextComponent` (both sides) to prefix a `"   "` piece carrying the item's style; the client walks each drawn chat line's flat siblings and draws the `SHOW_ITEM` item of any piece whose unformatted text starts with three spaces. ✔ Our fix = the same `"   "` piece with the hover.
- **The gold `Defiled Slime` lines were a matching bug, not special slimes.** Defiled slime (`defiledlands:slime_defiled`, plain `EntitySlime` split, no NBT copy) children each roll Scaling Health's Blight chance on their own first update (`DifficultyHandler.process`). When a Blight and a plain slime die in the same tick, both pending kills match `Blight Defiled Slime was killed by JCRVIII!` (their names are the same); the plain one claims it first, the Blight finds no line, is notable → our gold line. So the follow-ups under the Blight lines belonged to plain slimes. **Fixed** (2026-11-12): notable pending kills claim lines first, and a line containing `Blight <name>` only goes to a Blight (`ChatLines.claim(…, blight)`). Second cause, also fixed: identical lines in one tick (two same-name Blights) were merged by the per-player copy de-dup; copies now count per receiver (`ChatLines.add(…, receiver)`; the `EntityPlayerMP` hook passes the player, a broadcast is always a new line). Not done: requiring the killer's name in the line (fire/bleed deaths may not name them).
- **Scaling Health** `onBlightKilled`: `ChatHelper.translate` to **every player in the world** (one copy each, `EntityPlayerMP.sendMessage`), key `blight.killedByPlayer` with `blight.getName()` / killer name; pet kills credit the owner, the line names the pet. Non-player deaths: the vanilla message with `"Blight " + name`.
- **Champions 1.0.11.10**: sends `Champion <vanilla death message>` (`champions.identifier`) to everyone when tier ≥ `Death Message Tier` (**5** in the pack), killer must be a player. FermiumMixins `Death Message Tweak (Champions)=true` swaps in the champion's random name → the `Lancelot Dreamcatcher Defiled Slime was slain by …` line. Data: `ForgeCaps/champions:championship` `{tier:int, name:string, affixes:[{identifier, data}]}` (name/affixes only when tier > 0). Pack ranks: tiers 1–5 (`config/champions/ranks.json`). FermiumMixins also keeps Champions and Infernals apart.
- **InfernalMobs 1.7.6**: `ForgeData/InfernalMobsMod` = space-separated modifier names (trailing space); rank by modifier count: ≤ 5 rare/Elite, ≤ 10 Ultra, > 10 Infernal (`MobModifier.getEntityDisplayName`). Sends no kill line. Pack `eliteRarity=20`.
- **Ice and Fire 2.0.9**: `AgeTicks` (int, entity NBT), days = ticks / 24000; stage = 1, 2 at 25 d, 3 at 50, 4 at 75, 5 at 100 (`EntityDragonBase.getDragonStage`). Registry names (`CommonProxy`): `firedragon`, `lightningdragon` (`icedragon` ⚠ not seen in the string table, check with debug kill), `seaserpent`, `cyclops`, `gorgon`, `if_hydra`, `if_troll`, `myrmex_queen`, `stymphalianbird`, `amphithere`, `dread_lich`, `dread_knight`, `dread_beast`, `if_cockatrice`, `hippogryph`, `siren`.
- **SRParasites 1.9.21** ids (lang): `pri_*` Primitive, `ada_*` Adapted, `anc_dreadnaut` / `anc_overlord` Ancient, `beckon_si…sv`, `dispatcher_si…siv`, `rooter_si…siv`, plus `overseer`, `warden`, `marauder`, `monarch`, `architect`, `draconite`, `sim_*` / `fer_*` / `hi_*` (assimilated / feral / hijacked).

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

#### As built (2026-11-12)
- `killfeed/KillFeedListener`: pending kill at **HIGHEST** `LivingDeathEvent` (reads only; Scaling Health and Champions announce at NORMAL inside the event, vanilla after it), with vanilla's death message (`getCombatTracker().getDeathMessage()`) captured then. A LOWEST `receiveCanceled = true` listener gives the lines of a canceled death back.
- `ChatTap.seen` → `offer`: newest pending first, `KillLine.matches` (victim name, Blight rule, killer or pet name unless lingering credit); a match is held and the hook **cancels** the send (`MixinPlayerList`, `MixinEntityPlayerMP`, now `cancellable`). Per-player copies of one line merge per receiver. `ChatLines` is gone.
- Tick end: held → our line to the same audience (broadcast if any copy was a broadcast, else those players); no line by `lineWaitTicks` → our line only if notable (old rules, a trick shot, or an elite badge). Any exception or a line over `Chat.MAX_JSON` → the held originals go out unchanged.
- Our line: the captured `TextComponentTranslation` with arg 0 = badges + name (SHOW_TEXT hover: id, max HP, Champion/Infernal/dragon details, final hit, fight time), pet kills arg 1 = `Alice's Wolf`, `.item` keys arg 2 = our weapon; otherwise ` using` + the weapon appended. Extra: ` · 97m` (aqua, hover = trick tier/mount/flight/PB) if ≥ `trickshots.minDistance`, else ` · 1m12s` if ≥ `minFightSec`.
- Streaks: key = player + registry id; a kill is never collapsed if the victim is a player, a boss, elite, custom-named, in `announceMobs`, or a trick shot. Summary: `  …and N more <entity name> kills (B Blight) in 18s` (entity name is a translation key, the client translates).
- `killfeed/Badges`: Blight, `★`×tier, `Rare`/`Ultra`/`Infernal`, `Stage N`; one `writeToNBT` per announced kill. Colors: boss gold, elite red, Blight light purple.
- 0.4.0 (owner, 2026-11-12: "as on the boss bars"): Champions `★`×tier + the champion's name, both in the tier color (pack `ranks.json` → nearest chat color: yellow, gold, aqua, light purple, red; Champions' bar tints stars and name that way). InfernalMobs `★`×rank (1 Rare, 2 Ultra, 3 Infernal) in its bar's class colors (aqua, yellow, gold); its bar shows the class word, not stars, plus a random per-client title we can't reproduce. SRParasites `Phase N` (dark red) = `phasecreat`, the evolution phase (0–8; phases are on in the pack) the parasite was born in; hover adds `parasitekills`; elite from `killfeed.srpMinPhase` (6). Each badge keeps its color, the name keeps the rank color, one shared hover.
- 0.4.1 (owner, 2026-11-14: "Lost City phase 10 parasites spam the chat; only elite SRP"): `killfeed.srpListedOnly` (on): an `srparasites:*` kill nothing else announced gets our line only if it is in `announceMobs` or a boss; health, Blight, elite badges and trick shots don't count for SRP (SRPMixins gives Lost Cities x4 health, so `announceMinMaxHealth` hits nearly every parasite there). `srpMinPhase` default 0 (the Phase badge still shows). SRP defaults in `announceMobs`: `anc_overlord` (500 HP on this server), `anc_dreadnaut` (400), `draconite` (266), `sim_dragone` (Assimilated Ender Dragon, 260), `dispatcher_siv` (250), `beckon_siv` (220), `kirin` (no health entry); ids from SRP's own id list in `SRParasitesMobs.cfg` (1.9.21 has no `beckon_sv`). Kill lines another mod already sent are still replaced as before.
- Trick shots (`06`): with the kill feed live, `TrickShotListener` records as before but hands a `Callout` to the kill feed instead of broadcasting; only a new server record gets its own line (`  ★ new server record …`). Kill feed off or hooks missing → the old callout.
- `Chat.item` text now starts with three spaces when it has a hover (Quark icon, also `/trickshots`).
- 0.3.1 (owner, 2026-11-12): the weapon always shows its name, `using 🗡[Iron Longsword]` (Quark icon + `[name]` in the item's rarity color, italic if renamed, as vanilla); icon-only lines were blank without Quark and hid custom names.
- Tests: `KillLineTest` (matching, Blight rule, streak tail), `BadgesTest` (Infernal ranks, dragon stage).

Tests (in-game, need a client): Blight slime chain → first line, then one summary; a tier-5 Champion → one line (not Champions' + ours); arrow kill of a Blight at 50 m → `was shot by … using [icon] · 50m`, hover shows the tier; PvP → vanilla wording + icon; pet kill → `Alice's Wolf`; zombie → nothing; a client without Quark → blanks, tooltip on hover; `/dpatch debug kill 5` shows `replaced:` / `streak, counted` / `own line`.
