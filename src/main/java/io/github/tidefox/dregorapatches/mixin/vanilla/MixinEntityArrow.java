package io.github.tidefox.dregorapatches.mixin.vanilla;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.util.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Multishot/Splitshot volleys: vanilla bounces an arrow back (and resets the shooter grace) when the
 * hit is refused. Extra arrows in one tick hit a target in its i-frames, or the shooter's own
 * cancelled hurt, so they ricochet around and hit the player. An arrow that was refused by an
 * i-framed target or by its own shooter is removed instead. Shield blocks still bounce as in vanilla.
 */
@Mixin(EntityArrow.class)
public abstract class MixinEntityArrow {

    @Redirect(method = "onHit(Lnet/minecraft/util/math/RayTraceResult;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;attackEntityFrom(Lnet/minecraft/util/DamageSource;F)Z"),
            require = 0)
    private boolean dp$noRicochet(Entity target, DamageSource src, float amount) {
        boolean hurt = target.attackEntityFrom(src, amount);
        EntityArrow self = (EntityArrow) (Object) this;
        if (!hurt && !self.world.isRemote && (target == self.shootingEntity || target.hurtResistantTime > 0)) {
            self.setDead();
        }
        return hurt;
    }
}
