package io.github.tidefox.dregorapatches.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.tidefox.dregorapatches.nc.DpNcTile;
import io.github.tidefox.dregorapatches.nc.NcScope;
import io.github.tidefox.dregorapatches.nc.NcStats;
import net.minecraft.util.ITickable;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * docs/07 §3 steps 2 + 3: wraps the tile-entity tick call in {@code World.updateEntities}.
 * Frozen NC parts skip the whole tick; other NC tile entities tick inside {@link NcScope}.
 * Wrapping the call (not an NC method) covers work NC does after {@code super.update()}.
 */
@Mixin(World.class)
public abstract class MixinWorld {

    @Shadow @Final public boolean isRemote;

    @WrapOperation(method = "updateEntities",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ITickable;update()V"), require = 0)
    private void dp$tick(ITickable tile, Operation<Void> original) {
        if (this.isRemote || !(tile instanceof DpNcTile)) {
            original.call(tile);
            return;
        }
        if (((DpNcTile) tile).dp$isFrozen()) {
            NcStats.frozenTicks++;
            return;
        }
        NcScope.enter(tile);
        try {
            original.call(tile);
        } finally {
            NcScope.exit();
        }
    }
}
