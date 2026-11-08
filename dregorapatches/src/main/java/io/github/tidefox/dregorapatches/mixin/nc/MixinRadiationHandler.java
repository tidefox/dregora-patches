package io.github.tidefox.dregorapatches.mixin.nc;

import io.github.tidefox.dregorapatches.nc.NcScope;
import nc.radiation.RadiationHandler;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** docs/07 §3 step 3: the radiation world tick runs inside the NC scope (chunk guard). */
@Mixin(value = RadiationHandler.class, remap = false)
public abstract class MixinRadiationHandler {

    @Inject(method = "updateWorldRadiation", at = @At("HEAD"), require = 0)
    private void dp$enter(TickEvent.WorldTickEvent event, CallbackInfo ci) {
        if (!event.world.isRemote) NcScope.enterRadiation();
    }

    @Inject(method = "updateWorldRadiation", at = @At("RETURN"), require = 0)
    private void dp$exit(TickEvent.WorldTickEvent event, CallbackInfo ci) {
        if (!event.world.isRemote) NcScope.exit();
    }
}
