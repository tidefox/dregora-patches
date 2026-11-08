package io.github.tidefox.dregorapatches.mixin.levelup2;

import io.github.tidefox.dregorapatches.PatchesConfig;
import io.github.tidefox.dregorapatches.levelup2.FurnaceScan;
import levelup2.skills.crafting.FurnaceEfficiencyBonus;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** docs/08: replace the per-tick scan of every loaded tile entity with {@link FurnaceScan}. */
@Mixin(value = FurnaceEfficiencyBonus.class, remap = false)
public abstract class MixinFurnaceEfficiencyBonus {

    @Inject(method = "doFurnaceTicks", at = @At("HEAD"), cancellable = true, require = 0)
    private void dp$doFurnaceTicks(TickEvent.WorldTickEvent evt, CallbackInfo ci) {
        if (!PatchesConfig.levelup2.furnaceScanFix) return;
        FurnaceScan.tick(evt);
        ci.cancel();
    }
}
