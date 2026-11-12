package io.github.tidefox.dregorapatches.killfeed;

import javax.annotation.Nullable;

import io.github.tidefox.dregorapatches.PatchesConfig;
import io.github.tidefox.dregorapatches.mixinsupport.MixinState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.FMLCommonHandler;

/**
 * Sees every system chat line the server sends (death messages, Scaling Health's Blight line,
 * Champions' line, any mod's announcement). A line that is the kill line of a pending kill is held
 * back and replaced by the kill feed's own line at tick end (docs/03 §2.6). Fed by two vanilla hooks,
 * {@code mixin/vanilla/MixinPlayerList} and {@code MixinEntityPlayerMP}; player chat is not seen.
 * Our own lines are skipped ({@link #ours()}).
 */
public final class ChatTap {

    @Nullable
    static KillFeedListener sink;

    private static int ours;

    private ChatTap() {}

    /** True when the hooks applied: without them the kill feed can't replace lines and stays off. */
    public static boolean live() {
        return MixinState.isApplied("vanilla.MixinPlayerList") || MixinState.isApplied("vanilla.MixinEntityPlayerMP");
    }

    /**
     * Called from the vanilla hooks; {@code receiver} = the player for a per-player send, null for a
     * broadcast. True = the kill feed took the line, the hook cancels the send. Never throws.
     */
    public static boolean seen(ITextComponent c, @Nullable EntityPlayerMP receiver) {
        KillFeedListener s = sink;
        if (c == null || s == null || ours > 0 || !PatchesConfig.killfeed.enabled) {
            return false;
        }
        try {
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server == null || !server.isCallingFromMinecraftThread()) {
                return false;
            }
            return s.offer(c, TextFormatting.getTextWithoutFormattingCodes(c.getUnformattedText()), receiver);
        } catch (Throwable t) {
            return false; // a mod's broken translation must never break its own chat send
        }
    }

    /** Wrap our own sends: {@code ChatTap.ours(); try { send } finally { ChatTap.done(); }}. */
    public static void ours() {
        ours++;
    }

    public static void done() {
        ours--;
    }
}
