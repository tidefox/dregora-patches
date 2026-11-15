# 10 — Curse of Possession: no more voided items · Dregora Patches

Status: **built** (2026-11-15): `mixin/sme/MixinCurseOfPossession`; applies on a real Forge 2860 server with the pack's SoManyEnchantments jar (`ci/smoke.sh`). In-game test (§3) still to do with a client.

Goal: an item with **Curse of Possession** can't be thrown away (by design), but must never be deleted by accident. Only the pack's configured death deletion stays.

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
`MixinCurseOfPossession`: `@Inject` at HEAD of `onEntityJoinWorldEvent`, server only, cursed item, curse enabled:
- closest player creative or **dead** (a death drop: the dead player is still the closest entity) → SME's own code runs unchanged (death deletion stays as configured);
- otherwise SME's handler is skipped: the item goes into the closest player's inventory if it fits (spawn canceled, a partial fit leaves the rest on the ground), else it stays a **normal drop** (vanilla 5 min, picked up as soon as there's room). The toss fallback still sets no pickup delay + invulnerable.

Q / drag-out with room still put the item straight back (SME's toss handler, untouched). Config `sme.possessionNoVoid` (default `true`, `/dpatch reload`). Applied? → `/dpatch mixins`.

Not changed: a death drop with a teammate standing next to you is still deleted (SME only checks the closest player, which is you). Setting `Curse Of Possession Death Deletion = false` in `somanyenchantments.cfg` only changes that to 10 s.

## 3. In-game test (needs a client)
1. Fill the 36 main slots, wear a cursed chestplate, drag it out of the inventory window → it lands at your feet and stays; free a slot → walk over it → picked up.
2. Same with room in the inventory → goes straight back into the inventory (unchanged).
3. Q a cursed hotbar item → stays in the inventory (unchanged).
4. Die holding a cursed item → it's deleted (unchanged, death deletion).
5. `sme.possessionNoVoid = false` + `/dpatch reload` → step 1 voids the item again.
