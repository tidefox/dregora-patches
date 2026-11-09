package io.github.tidefox.dregorapatches.mixin.vanilla;

import io.github.tidefox.dregorapatches.killfeed.ChatTap;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.ITextComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * docs/03 §2.5: read-only tap on per-player system messages (team death messages, mods that send
 * their announcement to each player). Action-bar messages are skipped.
 */
@Mixin(EntityPlayerMP.class)
public abstract class MixinEntityPlayerMP {

    @Inject(method = "sendMessage(Lnet/minecraft/util/text/ITextComponent;)V", at = @At("HEAD"), require = 0)
    private void dp$tapMessage(ITextComponent component, CallbackInfo ci) {
        ChatTap.seen(component);
    }

    @Inject(method = "sendStatusMessage(Lnet/minecraft/util/text/ITextComponent;Z)V", at = @At("HEAD"), require = 0)
    private void dp$tapStatus(ITextComponent component, boolean actionBar, CallbackInfo ci) {
        if (!actionBar) {
            ChatTap.seen(component);
        }
    }
}
