package io.github.tidefox.dregorapatches.killfeed;

import io.github.tidefox.dregorapatches.PatchesConfig;
import io.github.tidefox.dregorapatches.mixinsupport.MixinState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.FMLCommonHandler;

/**
 * Sees every system chat line the server sends (death messages, Scaling Health's Blight line, any
 * mod's announcement), so the kill feed can follow whatever the pack actually announced instead of
 * guessing from a fixed list (docs/03 §2.5). Fed by two read-only vanilla hooks,
 * {@code mixin/vanilla/MixinPlayerList} and {@code MixinEntityPlayerMP}; player chat is not seen.
 * Our own lines are skipped ({@link #ours()}).
 */
public final class ChatTap {

    public static final ChatLines LINES = new ChatLines();

    private static int ours;

    private ChatTap() {}

    /** True when the hooks applied and {@code killfeed.followPackLines} is on. */
    public static boolean live() {
        return PatchesConfig.killfeed.followPackLines
                && (MixinState.isApplied("vanilla.MixinPlayerList") || MixinState.isApplied("vanilla.MixinEntityPlayerMP"));
    }

    /** Called from the vanilla hooks. Never throws. */
    public static void seen(ITextComponent c) {
        if (c == null || ours > 0 || !PatchesConfig.killfeed.enabled || !PatchesConfig.killfeed.followPackLines) {
            return;
        }
        try {
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server == null || !server.isCallingFromMinecraftThread()) {
                return;
            }
            String text = TextFormatting.getTextWithoutFormattingCodes(c.getUnformattedText());
            LINES.add(server.getTickCounter(), text);
        } catch (Throwable t) {
            // A mod's broken translation must never break its own chat send.
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
