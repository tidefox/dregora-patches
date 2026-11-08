package io.github.tidefox.warband.recap;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import io.github.tidefox.warband.core.Chat;
import io.github.tidefox.warband.core.WarbandConfig;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;

/** {@code /recap} (your last death) and {@code /recap share} (post it to everyone, once). */
public final class CmdRecap extends CommandBase {

    @Override
    public String getName() {
        return "recap";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/recap [share]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    /** Vanilla denies non-ops every command except a few; this one is for everyone. */
    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP p = getCommandSenderAsPlayer(sender);
        if (!WarbandConfig.recap.enabled) {
            p.sendMessage(Chat.text("Death recap is disabled.", TextFormatting.GRAY));
            return;
        }
        RecapStore.Death d = RecapModule.INSTANCE.lastDeath(p);
        if (d == null) {
            p.sendMessage(Chat.text("No death recorded since the server started.", TextFormatting.GRAY));
            return;
        }
        if (args.length == 0) {
            p.sendMessage(RecapModule.INSTANCE.message(d, p.getName(), false));
            return;
        }
        if (args.length == 1 && "share".equals(args[0])) {
            if (!WarbandConfig.recap.allowShare) {
                p.sendMessage(Chat.text("Sharing recaps is disabled.", TextFormatting.GRAY));
            } else if (d.shared) {
                p.sendMessage(Chat.text("You already shared this recap.", TextFormatting.GRAY));
            } else {
                d.shared = true;
                Chat.broadcast(server, RecapModule.INSTANCE.message(d, p.getName(), true));
            }
            return;
        }
        throw new WrongUsageException(getUsage(sender));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
            @Nullable BlockPos targetPos) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "share") : Collections.<String>emptyList();
    }
}
