package io.github.tidefox.dregorapatches.cmd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.Nullable;

import io.github.tidefox.dregorapatches.DregoraPatches;
import io.github.tidefox.dregorapatches.PatchesConfig;
import io.github.tidefox.dregorapatches.Tags;
import io.github.tidefox.dregorapatches.combat.DebugProbe;
import io.github.tidefox.dregorapatches.core.DregoraPatchesPlugin;
import io.github.tidefox.dregorapatches.diag.ChunkLoadTracer;
import io.github.tidefox.dregorapatches.levelup2.FurnaceScan;
import io.github.tidefox.dregorapatches.mixinsupport.MixinState;
import io.github.tidefox.dregorapatches.nc.DpNcTile;
import io.github.tidefox.dregorapatches.nc.FrozenTypes;
import io.github.tidefox.dregorapatches.nc.NcScope;
import io.github.tidefox.dregorapatches.nc.NcStats;
import io.github.tidefox.dregorapatches.reactor.ReactorClaims;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;

/**
 * {@code /dpatch} (op): {@code mixins}, {@code nc stats}, {@code levelup2 stats}, {@code chunkloads <seconds>},
 * {@code reload}, {@code debug damage [n]}, {@code debug kill [n]}, {@code debug off}.
 * All text is literal: clients do not have our lang file.
 */
public class CmdDpatch extends CommandBase {

    private static final String USAGE = "/dpatch mixins | nc stats | levelup2 stats | chunkloads <seconds> | reload"
            + " | debug damage [n] | debug kill [n] | debug off";

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
        } else if (args.length == 2 && "nc".equals(args[0]) && "stats".equals(args[1])) {
            for (String l : ncStats(server)) send(sender, l);
        } else if (args.length == 2 && "levelup2".equals(args[0]) && "stats".equals(args[1])) {
            levelup2Stats(sender);
        } else if (args.length == 2 && "chunkloads".equals(args[0])) {
            int s = parseInt(args[1], 1, 3600);
            if (ChunkLoadTracer.start(sender, s)) {
                send(sender, "Tracing non-player chunk loads for " + s + " s; the report goes to the log"
                        + (sender instanceof EntityPlayerMP ? " and to you." : "."));
            } else {
                send(sender, TextFormatting.RED + "A trace is already running.");
            }
        } else if (args.length == 1 && "reload".equals(args[0])) {
            ConfigManager.sync(Tags.MOD_ID, Config.Type.INSTANCE);
            DregoraPatches.LOGGER.info("Config reloaded");
            send(sender, "Dregora Patches config reloaded.");
        } else if (args.length >= 2 && "debug".equals(args[0])) {
            debug(sender, args);
        } else {
            throw new WrongUsageException(USAGE);
        }
    }

    private void debug(ICommandSender sender, String[] args) throws CommandException {
        java.util.UUID to = sender instanceof EntityPlayerMP ? ((EntityPlayerMP) sender).getUniqueID() : null;
        if (args.length == 2 && "off".equals(args[1])) {
            DebugProbe.INSTANCE.off();
            send(sender, "Debug probe off.");
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
            send(sender, "Logging the next " + n + (damage ? " hits involving a player." : " deaths caused by a player.")
                    + " Output: server log" + (to != null ? " and your chat." : "."));
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

    private static String yn(boolean b) {
        return b ? TextFormatting.GREEN + "yes" + TextFormatting.RESET : TextFormatting.RED + "no" + TextFormatting.RESET;
    }

    private static String onOff(boolean b) {
        return b ? TextFormatting.GREEN + "on" + TextFormatting.RESET : TextFormatting.GRAY + "off" + TextFormatting.RESET;
    }

    /** Also used for the periodic log line ({@code nc.logStats}). */
    public static List<String> ncStats(MinecraftServer server) {
        List<String> out = new ArrayList<String>();
        out.add(TextFormatting.GOLD + "NuclearCraft patches" + TextFormatting.RESET
                + " - freeze " + onOff(PatchesConfig.nc.freezeReactorParts)
                + ", chunk guard " + onOff(PatchesConfig.nc.chunkGuard));
        out.add(" patched: tick wrapper " + yn(MixinState.isApplied("vanilla.MixinWorld"))
                + ", NCTile " + yn(MixinState.isApplied("nc.MixinNCTile"))
                + ", radiation scope " + yn(MixinState.isApplied("nc.MixinRadiationHandler"))
                + ", chunk guard " + yn(MixinState.isApplied("vanilla.MixinChunkProviderServer")));
        // dim → label → {frozen, ticking}
        Map<Integer, Map<String, int[]>> byDim = new TreeMap<Integer, Map<String, int[]>>();
        if (server != null) {
            for (WorldServer w : server.worlds) {
                if (w == null) continue;
                for (TileEntity te : new ArrayList<TileEntity>(w.loadedTileEntityList)) {
                    if (!(te instanceof DpNcTile) || !((DpNcTile) te).dp$isFrozenType()) continue;
                    Map<String, int[]> m = byDim.get(w.provider.getDimension());
                    if (m == null) byDim.put(w.provider.getDimension(), m = new TreeMap<String, int[]>());
                    String label = FrozenTypes.label(te.getClass());
                    int[] n = m.get(label);
                    if (n == null) m.put(label, n = new int[2]);
                    n[((DpNcTile) te).dp$isFrozen() ? 0 : 1]++;
                }
            }
        }
        if (byDim.isEmpty()) {
            out.add(" reactor parts loaded: none");
        }
        for (Map.Entry<Integer, Map<String, int[]>> d : byDim.entrySet()) {
            StringBuilder sb = new StringBuilder(" dim ").append(d.getKey()).append(':');
            for (Map.Entry<String, int[]> e : d.getValue().entrySet()) {
                sb.append(' ').append(e.getKey()).append(' ').append(e.getValue()[0]).append(" frozen / ")
                        .append(e.getValue()[1]).append(" ticking;");
            }
            out.add(sb.toString());
        }
        out.add(" claims: " + ReactorClaims.all().size() + " (epoch " + ReactorClaims.epoch() + ", box +/-"
                + ReactorClaims.radius() + "), frozen ticks skipped: " + NcStats.frozenTicks);
        out.add(" guard: " + NcScope.blockedLoads() + " chunk load(s) blocked, " + NcScope.keptQueued()
                + " unload(s) not cancelled");
        List<String> loads = NcScope.breakdown(true);
        if (!loads.isEmpty()) out.add("   loads blocked by: " + String.join(", ", loads.subList(0, Math.min(8, loads.size()))));
        List<String> queued = NcScope.breakdown(false);
        if (!queued.isEmpty()) out.add("   unloads kept by: " + String.join(", ", queued.subList(0, Math.min(8, queued.size()))));
        return out;
    }

    private static void levelup2Stats(ICommandSender sender) {
        boolean applied = MixinState.isApplied("levelup2.MixinFurnaceEfficiencyBonus");
        send(sender, TextFormatting.GOLD + "Level Up! 2 furnace scan" + TextFormatting.RESET
                + " - fix " + onOff(PatchesConfig.levelup2.furnaceScanFix) + ", patched " + yn(applied));
        if (!applied) return;
        for (String l : FurnaceScan.stats()) send(sender, " " + l);
    }

    private static void send(ICommandSender sender, String text) {
        sender.sendMessage(new TextComponentString(text));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
            @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "mixins", "nc", "levelup2", "chunkloads", "reload", "debug");
        }
        if (args.length == 2 && "debug".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "damage", "kill", "off");
        }
        if (args.length == 2 && ("nc".equals(args[0]) || "levelup2".equals(args[0]))) {
            return getListOfStringsMatchingLastWord(args, "stats");
        }
        return Collections.emptyList();
    }
}
