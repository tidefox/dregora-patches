# 02 — Pings (Ping Wheel for 1.12.2, render-distance scoped)

Goal: Ping Wheel–style tactical pings with **who-pinged identity** and **locational scope**: you can ping anything within your render distance, and anyone whose render distance covers that spot sees it. Works with ItemPhysic flat items.

---

## 1. Reference

### Prior art
- **Ping Wheel** (1.20+): mark blocks/entities, distance-scaled markers, readable through terrain.
- **Ping (dmillerw)**: hold V → radial menu → typed ping to players in configurable range.

### Gaps we fill
| Gap | Ours |
|---|---|
| Hard to tell who pinged | skin face + name + per-player color |
| Fixed range | sender range = sender RD; receiver filter = receiver RD |
| Flat physics items unpickable by raycast | inflated item AABBs |

### APIs
| Need | API |
|---|---|
| Block raycast | `world.rayTraceBlocks(eye, end, false, true, false)` |
| Entity raycast | iterate `world.loadedEntityList`, `aabb.grow(r).calculateIntercept(eye, end)` |
| World render | `RenderWorldLastEvent` (billboard quads, depth test off for through-wall) |
| HUD / off-screen arrow | `RenderGameOverlayEvent.Post(ElementType.ALL)` |
| Skin face | `mc.getConnection().getPlayerInfo(uuid).getLocationSkin()`, UV face (8,8,8,8) + hat (40,8,8,8) on 64×64 |
| Item icon | `RenderItem#renderItemAndEffectIntoGUI` in HUD; `RenderItem` in world via billboard |
| Sound | `PositionedSoundRecord` at ping pos (local only, no server sound) |
| RD | client `renderDistanceChunks`; server `getPlayerList().getViewDistance()` |

---

## 2. Spec

### 2.1 Input
- Hold `ping.wheelKey` (default **V**, rebindable; middle-mouse optional) → radial wheel; release on segment = send.
- Tap (< 200 ms) = quick **MARK** at crosshair.
- Wheel segments: **MARK · DANGER · LOOT · ENEMY · GO HERE** (+ CANCEL center).

### 2.2 Targeting (client)
- Max distance `D = renderDistanceChunks × 16`.
- Block hit via `rayTraceBlocks` up to D.
- Entity hit: for each loaded entity within D, AABB inflated by
  - `EntityItem`: `max(0.5, dist × tan(1.5°))` ← ItemPhysic-safe
  - `EntityLivingBase`: `max(0.1, dist × tan(0.75°))`
  - others: skip
- Nearest intercept wins (entity before block if closer).
- Type auto-upgrade: MARK on `EntityItem` → LOOT; MARK on hostile mob → ENEMY.
- Miss (sky) → no ping, small "no target" toast.

### 2.3 Server validation
- Sender has mod + PROTO match.
- Same dimension; `pos` within `(senderRD × 16) + 16` of sender (horizontal); chunk loaded server-side.
- Entity ping: entity exists, within same bound; server re-reads its position (don't trust client pos).
- LOOT: server copies the `EntityItem` stack (count + NBT) into the broadcast.
- Rate limit: token bucket, 5 tokens, refill 1/s.

### 2.4 Recipient filter ("render distance to render distance")
Player P receives ping at `pos` iff:
- same dimension, P has mod,
- `horizDist(P, pos) ≤ min(P.renderDist, serverViewDist) × 16`,
- `ping.scope`: `ALL | ALLIES` (Allies from 00 §2.3), default ALL.
Sender always receives own ping. Players without mod: optional chat fallback `ping.chatFallback = true`:
`[Ping] Name ▸ DANGER @ 1231 72 3143` (hover shows type).

### 2.5 Display (client)
| Element | Behavior |
|---|---|
| World marker | billboard icon by type, pinger's skin face, name, distance `34m`; visible through walls (depth off, 60% alpha behind terrain) |
| Color | per player: `config override → team color → HSV hash(UUID)` |
| Scale | constant screen size up to 32 m, then shrinks to 60% at D |
| ENEMY | follows entity by id; if unloaded → freeze at last pos, gray out |
| LOOT | item icon + `×count`; disappears when item entity dies (picked up) |
| Off-screen | arrow on ring around crosshair, colored, with face icon |
| Sound | pitch by type; DANGER uses distinct tone; per-type volume config |
| Lifetime | `ping.lifetimeSec = 15`, fade last 2 s |
| Limits | max 3 active pings per player; new replaces oldest; same-spot re-ping refreshes |
| Clear | `ping.clearKey` clears own pings (sends CANCEL) |

### 2.6 Config
Server: `enabled, scope, chatFallback, bucketSize, refillPerSec, enabledTypes, maxDistanceCapChunks`.
Client: `wheelKey, tapToMark, throughWallAlpha, scaleMin, soundVolume, colorOverride`.

---

## 3. Implementation plan

```
ping/
  PingModule.java
  PingType.java                   enum: icon UV, sound pitch, default color
  net/PingReq.java  net/PingBcast.java
  server/PingService.java         validate, rate-limit, recipient filter, chat fallback
  client/PingKeys.java
  client/PingWheelGui.java        radial GuiScreen (no pause), mouse-angle segment select
  client/PingRaycast.java         block + inflated-entity ray
  client/ActivePings.java         store: per-pinger ring buffer, expiry, entity follow
  client/PingWorldRenderer.java   RenderWorldLastEvent billboards
  client/PingHudRenderer.java     off-screen arrows, faces
  client/Faces.java               skin face draw helper
```

### Steps
1. **Hello carries RD** (from core) — server stores per player; resend on RD change (poll `renderDistanceChunks` each second).
2. Quick-tap MARK on block only → PingReq → server → PingBcast → plain colored marker. Two dev clients.
3. Recipient filter: test two clients at RD 8 vs 16 at varying distances.
4. Entity raycast + ENEMY follow; LOOT with ItemPhysic in the pack instance (drop items on slopes, in water, far away).
5. Wheel GUI.
6. Identity: face, name, color, off-screen arrows.
7. Sound, lifetime/fade, limits, clear key.
8. Chat fallback for no-mod clients.
9. Perf: 6 players × 3 pings; profile `loadedEntityList` scan (only on key release, not per frame).

### Tests
- Ping at edge of sender RD → accepted; beyond → rejected silently.
- Receiver at RD 6 doesn't see a ping 120 blocks away; RD 12 does.
- Dimension change clears pings.
- Item picked up → LOOT ping vanishes for everyone.
