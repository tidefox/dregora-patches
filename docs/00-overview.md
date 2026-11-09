# 00 — Overview (server-side only)

Target server: **RLCraft Dregora 1.1.2b · Minecraft 1.12.2 · Forge 14.23.5.2860 · Java 8**. Full pack list: `reference/pack-mods.txt`.

Everything here runs **on the server only**. Players install nothing.

## One mod, one repo

| Mod | Mod ID | Where | What |
|---|---|---|---|
| **Dregora Patches** | `dregorapatches` | repo root (`tidefox/dregora-patches`) | coremod jar with two kinds of modules: **patches** (mixins into pack mods: NuclearCraft ruin tick spikes, Level Up! 2 furnace scan) and **features** (plain Forge event observers for this server: trick shots, kill feed) |

Everything is specific to RLCraft Dregora and ships to one server, so it's one jar and one CI pipeline. Each module has its own `<module>.enabled`-style switch, so a misbehaving module is turned off in `config/dregorapatches.cfg` without removing the jar.

History (2026-11-08): the separate **Warband** mod was dissolved. Trick shots and the kill feed moved in here; the death recap (`05`) was dropped; adaptive friendly fire moved to its own repo, **Friendly Fire Rework** (`tidefox/friendly-fire-rework`, handoff spec: `handoff/friendly-fire-rework.md`), since it's the one module that isn't Dregora-specific.

## Modules

| Doc | Module | Kind | Phase | Changes gameplay? |
|---|---|---|---|---|
| `07-nc-fission-fix.md` | NuclearCraft ruin fix: reactor freeze, chunk guard, tracer | patch | **P1** | freeze: reactors run only after a player claims them (`/reactor`). Guard: NC no longer loads chunks or keeps them loaded (`07 §2`) |
| `08-levelup2-furnace-scan.md` | Level Up! 2 per-tick tile-entity scan | patch | **P1** | no |
| `06-trick-shots.md` | Trick shots (announced mobs only) | feature | **P2** | no |
| `03-killfeed.md` | Kill feed (weapon follow-up line) | feature | P3 (code done) | no |

Per-mod versions, sources and the classes we touch: `reference/mods-context.md`.

Cut to ideas (need client install): `ideas/01-showcase.md`, `ideas/02-pings.md`, plus `ideas/IDEAS.md`. Dropped: death recap (was `05`; its First Aid findings live on in `handoff/friendly-fire-rework.md §0.1`).

## Patch rules
1. Mixins only in `mixin/`. Targets in mod classes use `@Mixin(value = X.class, remap = false)` and `require = 0` on every injector; member prefix `dp$`. Registered through FermiumBooter; no hard MixinBooter dependency.
2. Every patch has a config switch and a `/dpatch … stats` view.

## Observer rules (features: `trick/`, `killfeed/`, `combat/`)
1. **Passive observer**: subscribe at `EventPriority.LOWEST`, `receiveCanceled = false`; never cancel or modify an event. `/dpatch debug` listeners use `receiveCanceled = true` (read-only, only while armed).
2. **No reflection into, no imports of** other mods' classes, and no mixins. Detect special mobs through vanilla data (`06 §2.2 AnnouncedMobs`). One exception: the kill feed reads chat lines through `killfeed/ChatTap`, fed by two read-only vanilla hooks (`mixin/vanilla/MixinPlayerList`, `MixinEntityPlayerMP`: HEAD injects that only copy the text; `03 §2.5`). Without them it falls back to the static rules.
3. Vanilla/Forge clients without the jar must connect fine (`acceptableRemoteVersions = "*"`). All output is vanilla chat components (hover `SHOW_ITEM` / `SHOW_ENTITY`, click `RUN_COMMAND`), literal text.
4. Chat component JSON must stay < 32767 chars (`SPacketChat` limit kicks the receiver) — trim item NBT in hovers when needed.
5. First Aid 1.6.22 cancels every player `LivingHurtEvent` at LOWEST, so `LivingDamageEvent` doesn't fire for player victims. Anything that reads damage *to players* must read the hurt at `LOW` (`handoff/friendly-fire-rework.md §0.1`). Damage to mobs is unaffected.

## Shared feature core
| Piece | Used by |
|---|---|
| `combat/ProjectileOrigin` + `combat/ShotTag` — tags arrows on spawn with launcher stack + origin pos + flags (`dregorapatches:shot`) | 03, 06 |
| `combat/DebugProbe` — `/dpatch debug damage [n]` (hurt at HIGHEST/LOW/LOWEST, canceled?, damage event, health Δ after the tick), `/dpatch debug kill [n]` (registry id, boss flag, NBT/ForgeData/ForgeCaps keys, projectile, shot tag), `/dpatch debug off` | 03, 06 |
| `util/Chat`, `util/Fmt` — vanilla hovers, item NBT trimming, 30000-char JSON guard; number/text formatting | 03, 06 |
| `PatchesConfig` — Forge `@Config`, one category per module | all |

## Commands
| Command | Perm |
|---|---|
| `/trickshots [player]` | all |
| `/reactor list`, `/reactor claim [token]`, `/reactor unclaim <id>` | all |
| `/reactor list -exact`, `/reactor admin list [player] [-exact]`, `/reactor admin unclaim <id>`, `/reactor admin keeploaded <id> on\|off` | op |
| `/dpatch mixins`, `/dpatch chunkloads <seconds>`, `/dpatch nc stats`, `/dpatch levelup2 stats` | op |
| `/dpatch reload`, `/dpatch debug damage [n]`, `/dpatch debug kill [n]`, `/dpatch debug off` | op |

## Phases
| Phase | Deliverable | Done when |
|---|---|---|
| P0 ✔ | Scaffolded after `tidefox/skinbridge` (RFG 2.0.3); `./gradlew build` green; `runServer` boots; CI smoke boots Forge 2860 + FermiumBooter + the jar | jar builds, server starts with it in dev |
| P1 | Patches: owner records a baseline on NC 2.19a (no NC update) → one jar: NC reactor freeze (on), NC chunk guard (off, flip after measuring), Level Up! 2 scan fix (`07 §3`, `08`) | 15 min `spark tickmonitor --threshold-tick 150` without the 45 s spikes; `doFurnaceTicks` < 0.1% in a 30 min profile |
| P2 (code done) | feature core + 06 trick shots | trick shot fires only on announced mobs |
| P3 (code done) | 03 kill feed | per doc tests |

## Open decisions (defaults in use)
- Java package root: `io.github.tidefox.dregorapatches` (build layout follows `tidefox/skinbridge`, see `CLAUDE.md` Setup).
