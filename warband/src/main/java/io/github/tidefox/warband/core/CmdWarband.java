package io.github.tidefox.warband.core;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import io.github.tidefox.warband.Tags;
import io.github.tidefox.warband.Warband;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;

/**
 * {@code /wb} (op): {@code reload}, {@code debug damage [n]}, {@code debug kill [n]}, {@code debug off}.
 * All text is literal: clients do not have our lang file.
 */
public class CmdWarband extends CommandBase {

    private static final String USAGE = "/wb reload | /wb debug damage [n] | /wb debug kill [n] | /wb debug off";

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
        if (args.length >= 2 && "debug".equals(args[0])) {
            UUID to = sender instanceof EntityPlayerMP ? ((EntityPlayerMP) sender).getUniqueID() : null;
            if (args.length == 2 && "off".equals(args[1])) {
                DebugProbe.INSTANCE.off();
                sender.sendMessage(new TextComponentString("Warband debug off."));
                return;
            }
            if (args.length <= 3 && ("damage".equals(args[1]) || "kill".equals(args[1]))) {
                boolean damage = "damage".equals(args[1]);
                int n = args.length == 3 ? parseInt(args[2], 1, 100) : (damage ? 5 : 3);
                if (damage) {
                    DebugProbe.INSTANCE.armDamage(n, to);
                } else {
                    DebugProbe.INSTANCE.armKill(n, to);
                }
                sender.sendMessage(new TextComponentString("Logging the next " + n + (damage
                        ? " hits involving a player." : " deaths caused by a player.")
                        + " Output: server log" + (to != null ? " and your chat." : ".")));
                return;
            }
        }
        throw new WrongUsageException(USAGE);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
            @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "reload", "debug");
        }
        if (args.length == 2 && "debug".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "damage", "kill", "off");
        }
        return Collections.emptyList();
    }
}
