# 09 — Lycanites pet recall · Dregora Patches

Status: **built** (2026-11-09): `mixin/lycanites/MixinPetEntry` + `lycanites/PetRecall`; applies on a real Forge 2860 server with the pack's Lycanites jar (`ci/smoke.sh`). In-game test (§3) still to do with a client.

Goal: the **Teleport** button in Lycanites' summoning menu (pets/mounts tab) brings the pet back even when it was left in a chunk that has since unloaded. Before this patch it did nothing and the pet was lost until the owner dismissed and re-summoned it, or relogged.

Lives in **Dregora Patches** (late mixin config `mixins.dregorapatches.lycanites.json`). Server-only: the client already sends the request; only the server's handling changes.

## 1. Cause (Lycanites Mobs `lycanitesmobs-1.12.2-2.0.8.10`, CurseForge file 5750564, sha1 `56c49cf0…`, decompiled 2026-11-09)
- Every summoned pet/mount is a `com.lycanitesmobs.core.pets.PetEntry`, ticked from the owner's `ExtendedPlayer` → `PetManager.onUpdate` → `PetEntry.onUpdate(World)`.
- The GUI's Teleport button sets `teleportEntity = true` and sends `MessagePetEntry`; the server sets the same flag. In the next `onUpdate`, if `entity` is non-null and `entity.isEntityAlive()`: change dimension if needed, then `entity.setPosition(owner)`.
- When the pet's chunk unloads, vanilla takes the entity out of the world (`World.unloadEntities` → `onRemovedFromWorld`) but **does not set it dead**. `PetEntry.entity` still points at the detached object, it is "alive", so:
  - the entry never respawns it (respawn only runs once `entity` is null or dead),
  - Teleport moves an object that is not in any world: nothing happens.
- The copy written into the chunk carries `IsBoundPet = true`; when that chunk loads again, `BaseCreatureEntity.readEntityFromNBT` sees no pet entry and kills it. So the pet in the world is gone for good; only the detached object in the entry has its data.
- "Out of sight" but still in a loaded chunk works in vanilla Lycanites (the entity is in the world, `setPosition` moves it). In practice "out of sight" here means past the view distance, i.e. unloaded.

## 2. Fix
`MixinPetEntry`: `@Inject` at HEAD of `PetEntry.onUpdate`, server only, entry active and summoned:
- remembers the last entity of the entry seen in a world (`Entity.isAddedToWorld()`, Forge);
- if that same entity is now **not in a world, not dead**, and Teleport was requested (`teleportEntity`): `saveEntityNBT()` (level, XP, inventory incl. saddle/armour, age), drop the reference, `spawnEntity()` at the owner (Lycanites' own spawn: behaviour, name, subspecies, owner), then set its health back to what it had (capped at max). Lycanites' code then runs as usual and puts it on the owner.
- An entity that never made it into a world (spawn refused) or is dead is left to Lycanites, so this can't spawn in a loop.
- No spirit change (the entry stays summoned; spirit is reserved per entry, not per entity) and no duplicate: the chunk copy kills itself on load (§1).
- Without Teleport nothing changes: a pet left in an unloaded chunk stays "summoned" until the owner presses Teleport (or dismisses).

Config `lycanites.petRecallFix` (default `true`). `/dpatch lycanites stats`: patched?, recalls, failed respawns. Each recall logs `Lycanites: recalled unloaded pet <name> for <owner>`.

## 3. In-game test (needs a client)
1. Summon a mount, dismount, set it to Sit, walk/fly > view distance + 2 chunks away (`/dpatch chunkloads` not needed). Open the summoning menu → Teleport → mount appears at you with the same level, health, saddle/armour. Log shows the recall line.
2. Same with a pet near you (loaded): Teleport works as before, no log line.
3. Leave a pet, change dimension, Teleport → pet arrives in your dimension.
4. Go back to where it was left: no second copy.
5. `lycanites.petRecallFix = false` + `/dpatch reload` → old behaviour (Teleport does nothing for the unloaded pet).
