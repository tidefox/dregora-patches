# 10 — Curse of Possession: no more voided items · Dregora Patches

Status: **built** (2026-11-15): `mixin/sme/MixinCurseOfPossession`; applies on a real Forge 2860 server with the pack's SoManyEnchantments jar (`ci/smoke.sh`). In-game test (§3) still to do with a client.

Goal: an item with **Curse of Possession** can't be thrown away (by design), but must never be deleted by accident. On death it goes to a nearby teammate; only a solo death (or nobody with room) still deletes it.

Lives in **Dregora Patches** (late mixin config `mixins.dregorapatches.sme.json`). Server-only.

## 1. Cause (SoManyEnchantments `SoManyEnchantments-1.0.8-1.12.2.jar`, CurseForge 267035/7156865, sha1 `89613555…`, read with javap 2026-11-15; 1.0.9 `8980799` has the same code)
`com.shultrea.rin.enchantments.curses.EnchantmentCurseofPossession` has two `@SubscribeEvent(priority = HIGHEST)` handlers and nothing else (no mixin):
- `onItemTossEvent` (Q, dragging out of the window, closing a GUI with it on the cursor): puts a copy back with `inventory.addItemStackToInventory` and cancels the toss. If that fails (main 36 slots full), it drops the copy at the player instead (thrower = player, no pickup delay, invulnerable).
- `onEntityJoinWorldEvent`, for **every** `EntityItem` carrying the curse: closest player within 8 blocks; creative → nothing; alive and `addItemStackToInventory` works → item goes in, spawn canceled. **Otherwise** `lifespan = 5` ticks when `Curse Of Possession Death Deletion = true` (the pack's setting), else 200 ticks.

So the "death deletion" also hits every non-death case where nobody nearby has room:
- **Cursed armour / offhand / bauble dragged out of the window with a full main inventory** — the freed slot isn't one of the 36 `addItemStackToInventory` looks at → dropped → join → still full → gone in 5 ticks. (Q from the hotbar frees a main slot, so it comes back: that's why it only "sometimes" voids.)
- Item on the cursor taken from a chest/backpack/crafting grid, dropped or GUI closed with a full inventory.
- A mob, chest or item frame dropping a cursed item with no player within 8 blocks (ranged kill, broken container).
- The remainder of a stack that only partly fits (SME cancels the whole spawn).

## 2. Fix
`MixinCurseOfPossession`: `@Inject` at HEAD of `onEntityJoinWorldEvent`, server only, cursed item, curse enabled. "Give" = main inventory, then the offhand if it's empty.
- Closest player creative → SME's code (does nothing).
- **Death drop** (closest player is dead: they're still in the world, at the drop) → give it to the nearest **living teammate within 8 blocks**, trying the next-nearest if one has no room (spectators skipped). Co-op: the item stays with the team. Solo, or nobody has room → SME's deletion as before (`possessionDeathToTeammate`, default `true`).
- **Anything else** → give it to the closest player; if it doesn't fit, it stays a **normal drop** (vanilla 5 min, picked up once there's room) instead of vanishing in 5 ticks. A partial fit leaves the rest on the ground. The toss fallback still sets no pickup delay + invulnerable (`possessionNoVoid`, default `true`).

Q / drag-out with room still put the item straight back (SME's toss handler, untouched). Both toggles: `/dpatch reload`. Applied? → `/dpatch mixins`.

Corpse Complex (in the pack) edits the death drop list before the items spawn: anything it keeps or destroys never reaches this hook. ⚠ verify in-game that cursed items still drop as items on death.

## 3. In-game test (needs a client)
1. Fill the 36 main slots, wear a cursed chestplate, drag it out of the inventory window → it lands at your feet and stays; free a slot → walk over it → picked up.
2. Same with room in the inventory → goes straight back into the inventory (unchanged).
3. Q a cursed hotbar item → stays in the inventory (unchanged).
4. Die holding a cursed item alone → it's deleted (unchanged, death deletion).
5. Die holding a cursed item with a teammate within 8 blocks → it lands in their inventory; with their main inventory full → their empty offhand; both full → deleted.
6. Fill the 36 main slots with an empty offhand, drag cursed armour out → it goes into your offhand.
7. `sme.possessionNoVoid = false` / `sme.possessionDeathToTeammate = false` + `/dpatch reload` → old behaviour for steps 1 / 5.
