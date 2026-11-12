package io.github.tidefox.dregorapatches.mixin.nc;

import java.util.concurrent.ConcurrentMap;

import nc.config.NCConfig;
import nc.radiation.environment.RadiationEnvironmentHandler;
import nc.radiation.environment.RadiationEnvironmentInfo;
import nc.tile.radiation.ITileRadiationEnvironment;
import nc.util.FourPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * docs/07 §4.8: NC's removeTile walks every entry of both environment maps (≈729 per scrubber ever loaded,
 * never pruned on unload) and fires on every scrubber re-add when a chunk loads mid-tick → multi-second ticks.
 * A scrubber only ever registers in its own (2r+1)³ cube (TileRadiationScrubber.onAdded), so look up just those keys.
 */
@Mixin(value = RadiationEnvironmentHandler.class, remap = false)
public abstract class MixinRadiationEnvironmentHandler {

    @Shadow @Final private static ConcurrentMap<FourPos, RadiationEnvironmentInfo> ENVIRONMENT;
    @Shadow @Final private static ConcurrentMap<FourPos, RadiationEnvironmentInfo> ENVIRONMENT_BACKUP;

    @Inject(method = "removeTile", at = @At("HEAD"), cancellable = true, require = 0)
    private static void dp$removeTile(ITileRadiationEnvironment tile, CallbackInfo ci) {
        // by name: TileRadiationScrubber implements IC2 interfaces and can't be compiled against; others keep the full scan
        if (!"nc.tile.radiation.TileRadiationScrubber".equals(tile.getClass().getName())) return;
        FourPos pos = tile.getFourPos();
        int r = NCConfig.radiation_scrubber_radius;
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            FourPos key = pos.add(x, y, z);
            dp$remove(ENVIRONMENT, key, pos);
            dp$remove(ENVIRONMENT_BACKUP, key, pos);
        }
        ci.cancel();
    }

    private static void dp$remove(ConcurrentMap<FourPos, RadiationEnvironmentInfo> map, FourPos key, FourPos tilePos) {
        RadiationEnvironmentInfo info = map.get(key);
        if (info == null) return;
        info.tileMap.remove(tilePos);
        if (info.tileMap.isEmpty()) map.remove(key, info);
    }
}
