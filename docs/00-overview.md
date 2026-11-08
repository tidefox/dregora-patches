# 00 — Overview (server-side only)

Target server: **RLCraft Dregora 1.1.2b · Minecraft 1.12.2 · Forge 14.23.5.2860 · Java 8**. Full pack list: `reference/pack-mods.txt`.

Everything here runs **on the server only**. Players install nothing.

## Two mods, one repo

| Mod | Mod ID | Folder | What | Touches other mods? |
|---|---|---|---|---|
| **Warband** | `warband` | `warband/` | co-op QoL: death recap, trick shots, then kill feed + adaptive friendly fire | **No** — Forge events + vanilla NBT only |
| **Dregora Patches** | `dregorapatches` | `dregorapatches/` | performance/bug fixes for pack mods; first: NuclearCraft ruin tick spikes, Level Up! 2 furnace scan | **Yes** — mixins via FermiumBooter (coremod) |

Kept separate on purpose: Warband must stay a plain, removable mod; Patches is a coremod that can be dropped the moment upstream fixes land.

## Modules

| Doc | Module | Mod | Phase | Changes gameplay? |
|---|---|---|---|---|
| `07-nc-fission-fix.md` | NuclearCraft ruin fix: result cache, chunk guard, tracer | Patches | **P1** | cache: no. Guard: NC no longer loads chunks or keeps them loaded (`07 §2`) |
| `08-levelup2-furnace-scan.md` | Level Up! 2 per-tick tile-entity scan | Patches | **P1** | no |
| `05-death-recap.md` | Private death recap | Warband | **P2** | no |
| `06-trick-shots.md` | Trick shots (announced mobs only) | Warband | **P2** | no |
| `03-killfeed.md` | Kill feed (weapon, assists) | Warband | P3 | no |
| `04-friendly-fire.md` | Adaptive friendly fire | Warband | P3 | **yes** — opt-in via config, off by default |

Cut to ideas (need client install): `ideas/01-showcase.md`, `ideas/02-pings.md`, plus `ideas/IDEAS.md`.

## Warband rules
1. **Passive observer** for 03/05/06: subscribe at `EventPriority.LOWEST`, `receiveCanceled = false`; never cancel or modify an event.
2. **No reflection, no imports** of other mods' classes. Detect special mobs through vanilla data (`06 §2.2 AnnouncedMobs`).
3. `@Mod(acceptableRemoteVersions = "*", serverSideOnly = true)` semantics: vanilla/Forge clients without the jar must connect fine. All output is vanilla chat components (hover `SHOW_ITEM` / `SHOW_ENTITY`, click `RUN_COMMAND`).
4. 04 (friendly fire) is the only module allowed to cancel/modify damage; config `ff.enabled = false` by default.
5. Chat component JSON must stay < 32767 chars (`SPacketChat` limit kicks the receiver) — trim item NBT in hovers when needed.

## Shared Warband core
| Piece | Used by |
|---|---|
| `core/ProjectileOrigin` — tags arrows on spawn with launcher stack + origin pos + flags | 03, 05, 06 |
| `core/DebugDamage` — `/wb debug damage [n]` | 03, 04, 05 |
| `core/DebugKill` — `/wb debug kill [n]` | 03, 06 |
| `core/Allies` — `ALL_PLAYERS` (default) / `VANILLA_TEAM` | 04 |
| `core/WarbandConfig` — Forge `@Config` | all |

## Commands
| Command | Mod | Perm |
|---|---|---|
| `/recap`, `/recap share` | Warband | all |
| `/trickshots [player]` | Warband | all |
| `/ff status` | Warband | all |
| `/wb debug damage [n]`, `/wb debug kill [n]`, `/wb reload` | Warband | op |
| `/dpatch chunkloads <seconds>`, `/dpatch nc stats`, `/dpatch levelup2 stats` | Patches | op |

## Phases
| Phase | Deliverable | Done when |
|---|---|---|
| P0 | Both folders scaffolded from CleanroomMC TemplateDevEnv; `./gradlew build` green; `runServer` boots | jars build, server starts with jars in dev |
| P1 | Patches: owner moves NC to 2.19d (baseline) → one jar: NC result cache (on), NC chunk guard (off, flip after measuring), Level Up! 2 scan fix (`07 §3`, `08`) | 15 min `spark tickmonitor --threshold-tick 150` without the 45 s spikes; `doFurnaceTicks` < 0.1% in a 30 min profile |
| P2 | Warband: core + 05 + 06 | recap delivered on respawn; trick shot fires only on announced mobs |
| P3 | Warband: 03, then 04 | per doc tests |

## Open decisions (defaults in use)
- Java package root: `io.github.<github-handle>` — **ask the user** before scaffolding.
- FF ally mode `ALL_PLAYERS`, curve `1.0/1.0/0.6/0.3/0.0`, lethal floor 4 HP.
