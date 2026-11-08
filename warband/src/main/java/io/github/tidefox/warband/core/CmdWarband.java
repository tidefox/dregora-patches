package io.github.tidefox.warband.core;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import io.github.tidefox.warband.Tags;
import io.github.tidefox.warband.Warband;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;

/**
 * {@code /wb} (op). P0: {@code /wb reload}. P2 adds {@code debug damage|kill}.
 * All text is literal: clients do not have our lang file.
 */
public class CmdWarband extends CommandBase {

    private static final String USAGE = "/wb reload";

    @Override
    public String getName() {
        return "wb";
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
        if (args.length == 1 && "reload".equals(args[0])) {
            ConfigManager.sync(Tags.MOD_ID, Config.Type.INSTANCE);
            Warband.LOGGER.info("Config reloaded");
            sender.sendMessage(new TextComponentString("Warband config reloaded."));
            return;
        }
        throw new WrongUsageException(USAGE);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
            @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "reload");
        }
        return Collections.emptyList();
    }
}
