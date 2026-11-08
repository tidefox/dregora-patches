package io.github.tidefox.dregorapatches.trick;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import io.github.tidefox.dregorapatches.util.Chat;
import io.github.tidefox.dregorapatches.util.Fmt;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

/** {@code /trickshots} (server top 10) and {@code /trickshots <player>} (their best). */
public final class CmdTrickShots extends CommandBase {

    @Override
    public String getName() {
        return "trickshots";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/trickshots [player]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        RecordBook book = ShotRecords.get(server).book;
        if (args.length == 0) {
            List<RecordBook.Entry> top = book.top();
            if (top.isEmpty()) {
                sender.sendMessage(Chat.text("No trick shots yet.", TextFormatting.GRAY));
                return;
            }
            TextComponentString root = new TextComponentString("");
            root.appendSibling(Chat.text("➶ Trick shot records", TextFormatting.GOLD));
            for (int i = 0; i < top.size(); i++) {
                RecordBook.Entry e = top.get(i);
                root.appendSibling(new TextComponentString("\n " + (i + 1) + ". "));
                root.appendSibling(Chat.hoverText(e.player + " — " + Fmt.meters(e.distance),
                        e.mob + "\n" + date(e.date), TextFormatting.WHITE));
                root.appendSibling(Chat.text(" on " + e.mob, TextFormatting.GRAY));
            }
            sender.sendMessage(root);
            return;
        }
        if (args.length == 1) {
            RecordBook.Entry e = book.bestByName(args[0]);
            if (e == null) {
                sender.sendMessage(Chat.text(args[0] + " has no trick shot yet.", TextFormatting.GRAY));
            } else {
                sender.sendMessage(Chat.text("➶ " + e.player + "'s best: " + Fmt.meters(e.distance) + " on "
                        + e.mob + " (" + date(e.date) + ")", TextFormatting.GOLD));
            }
            return;
        }
        throw new WrongUsageException(getUsage(sender));
    }

    private static String date(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(millis));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
            @Nullable BlockPos targetPos) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames())
                : Collections.<String>emptyList();
    }
}
