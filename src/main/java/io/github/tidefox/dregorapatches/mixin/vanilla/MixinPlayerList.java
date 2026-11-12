package io.github.tidefox.dregorapatches.mixin.vanilla;

import io.github.tidefox.dregorapatches.killfeed.ChatTap;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.text.ITextComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * docs/03 §2.6: tap on server-wide system messages (death messages, mod announcements). A kill
 * line the kill feed takes is canceled here and sent again, as one line, at tick end.
 */
@Mixin(PlayerList.class)
public abstract class MixinPlayerList {

    @Inject(method = "sendMessage(Lnet/minecraft/util/text/ITextComponent;Z)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void dp$tapBroadcast(ITextComponent component, boolean isSystem, CallbackInfo ci) {
        if (isSystem && ChatTap.seen(component, null)) { // isSystem false = a player's chat message
            ci.cancel();
        }
    }
}
