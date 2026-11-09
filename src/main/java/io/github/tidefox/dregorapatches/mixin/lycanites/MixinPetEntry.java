package io.github.tidefox.dregorapatches.mixin.lycanites;

import com.lycanitesmobs.core.pets.PetEntry;
import io.github.tidefox.dregorapatches.PatchesConfig;
import io.github.tidefox.dregorapatches.lycanites.PetRecall;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * docs/09: Lycanites "Teleport" (summoning menu) for a pet/mount whose chunk was unloaded.
 *
 * <p>When the chunk unloads, the pet leaves the world but {@code PetEntry.entity} keeps
 * pointing at it. It is not dead, so the entry never respawns it, and Teleport only moves
 * that detached object. (The copy saved in the chunk kills itself when it loads again,
 * because it has no pet entry.) Here: on Teleport, if the entity was in a world and is
 * not any more, save it into the entry (level, XP, inventory) and spawn it fresh at the
 * owner, keeping its health. Lycanites' own code then runs as usual.
 */
@Mixin(value = PetEntry.class, remap = false)
public abstract class MixinPetEntry {

    @Shadow public Entity entity;
    @Shadow public EntityLivingBase host;
    @Shadow public boolean active;
    @Shadow public boolean spawningActive;
    @Shadow public boolean teleportEntity;
    @Shadow public boolean isRespawning;
    @Shadow public int respawnTime;

    @Shadow public abstract void saveEntityNBT();

    @Shadow public abstract void spawnEntity();

    /** Last entity of this entry seen in a world; only that one can go stale. */
    @Unique private Entity dp$seen;

    @Inject(method = "onUpdate", at = @At("HEAD"), require = 0)
    private void dp$onUpdate(World world, CallbackInfo ci) {
        if (world.isRemote || !this.active || !this.spawningActive) return;
        Entity e = this.entity;
        if (e == null) {
            this.dp$seen = null;
            return;
        }
        if (e.isAddedToWorld()) {
            this.dp$seen = e;
            return;
        }
        // Never seen in a world (spawn refused?) or dead: leave it to Lycanites.
        if (e != this.dp$seen || e.isDead || !this.teleportEntity || this.host == null) return;
        if (!PatchesConfig.lycanites.petRecallFix) return;

        float health = e instanceof EntityLivingBase ? ((EntityLivingBase) e).getHealth() : -1;
        this.saveEntityNBT();
        this.entity = null;
        this.dp$seen = null;
        this.isRespawning = false;
        this.respawnTime = 0;
        this.spawnEntity();
        Entity fresh = this.entity;
        if (fresh instanceof EntityLivingBase && health > 0) {
            EntityLivingBase l = (EntityLivingBase) fresh;
            l.setHealth(Math.min(health, l.getMaxHealth()));
        }
        if (fresh != null && fresh.isAddedToWorld()) this.dp$seen = fresh;
        PetRecall.recalled(this.host, fresh != null ? fresh : e, fresh != null);
    }
}
