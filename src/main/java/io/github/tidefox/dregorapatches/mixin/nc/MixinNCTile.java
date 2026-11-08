package io.github.tidefox.dregorapatches.mixin.nc;

import io.github.tidefox.dregorapatches.PatchesConfig;
import io.github.tidefox.dregorapatches.nc.DpNcTile;
import io.github.tidefox.dregorapatches.nc.FrozenTypes;
import io.github.tidefox.dregorapatches.reactor.ReactorClaims;
import nc.tile.NCTile;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * docs/07 §3 step 2: adds {@link DpNcTile} to every NuclearCraft tile entity. The freeze itself
 * happens in {@code mixin.vanilla.MixinWorld} around the tick call. No injectors here.
 */
@Mixin(value = NCTile.class, remap = false)
public abstract class MixinNCTile extends TileEntity implements DpNcTile {

    /** -1 unknown, 0 not a frozen type, 1 frozen type */
    @Unique private byte dp$type = -1;
    @Unique private boolean dp$frozen;
    @Unique private int dp$epoch = Integer.MIN_VALUE;
    /** World time before which a just-claimed part keeps skipping ticks (staggered wake-up). */
    @Unique private long dp$thawAt;

    @Override
    public boolean dp$isFrozenType() {
        if (dp$type < 0) dp$type = FrozenTypes.isFrozenType(((Object) this).getClass()) ? (byte) 1 : (byte) 0;
        return dp$type == 1;
    }

    @Override
    public boolean dp$isFrozen() {
        if (!PatchesConfig.nc.freezeReactorParts || !dp$isFrozenType()) return false;
        int e = ReactorClaims.epoch();
        if (e != dp$epoch) {
            boolean wasFrozen = dp$frozen && dp$epoch != Integer.MIN_VALUE;
            dp$frozen = !ReactorClaims.covers(this.world.provider.getDimension(), this.pos);
            dp$epoch = e;
            if (wasFrozen && !dp$frozen) {
                // Claim just unfroze this part: spread first ticks of a whole reactor over 20 ticks.
                dp$thawAt = this.world.getTotalWorldTime() + Math.floorMod(this.pos.hashCode(), 20);
            }
        }
        if (dp$frozen) return true;
        return dp$thawAt != 0 && this.world.getTotalWorldTime() < dp$thawAt;
    }
}
