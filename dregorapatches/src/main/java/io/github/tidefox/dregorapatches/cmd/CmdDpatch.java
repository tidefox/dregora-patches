package io.github.tidefox.dregorapatches.cmd;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import io.github.tidefox.dregorapatches.DregoraPatches;
import io.github.tidefox.dregorapatches.Tags;
import io.github.tidefox.dregorapatches.core.DregoraPatchesPlugin;
import io.github.tidefox.dregorapatches.mixinsupport.MixinState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

/**
 * {@code /dpatch} (op). P0: {@code /dpatch mixins} lists the applied mixins per config.
 * P1 adds {@code nc stats}, {@code levelup2 stats} and {@code chunkloads}.
 * All text is literal: clients do not have our lang file.
 */
public class CmdDpatch extends CommandBase {

    private static final String USAGE = "/dpatch mixins";

    @Override
    public String getName() {
        return "dpatch";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return USAGE;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 1 && "mixins".equals(args[0])) {
            mixins(sender);
            return;
        }
        throw new WrongUsageException(USAGE);
    }

    private static void mixins(ICommandSender sender) {
        send(sender, TextFormatting.GOLD + "Dregora Patches " + Tags.VERSION
                + TextFormatting.RESET + " - enqueued with FermiumBooter: "
                + (DregoraPatchesPlugin.isEnqueued() ? TextFormatting.GREEN + "yes" : TextFormatting.RED + "no"));
        String[][] configs = {
                {DregoraPatchesPlugin.CONFIG_VANILLA, "vanilla"},
                {DregoraPatchesPlugin.CONFIG_NC, "nc"},
                {DregoraPatchesPlugin.CONFIG_LEVELUP2, "levelup2"},
        };
        Map<String, Set<String>> applied = MixinState.snapshot();
        int total = 0;
        for (String[] c : configs) {
            Set<String> set = applied.get(c[0]);
            int n = set == null ? 0 : set.size();
            total += n;
            send(sender, " " + c[0] + ": loaded " + (MixinState.isConfigLoaded(c[1]) ? "yes" : "no") + ", " + n + " applied");
            if (set != null) {
                for (String m : set) {
                    send(sender, "   " + TextFormatting.GRAY + m.substring(MixinState.MIXIN_PACKAGE.length()));
                }
            }
        }
        DregoraPatches.LOGGER.info("/dpatch mixins: {} mixin(s) applied", total);
    }

    private static void send(ICommandSender sender, String text) {
        sender.sendMessage(new TextComponentString(text));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
            @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "mixins");
        }
        return Collections.emptyList();
    }
}
