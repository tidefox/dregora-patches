package io.github.tidefox.dregorapatches.diag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.tidefox.dregorapatches.DregoraPatches;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * {@code /dpatch chunkloads <seconds>} (docs/07 §3): records every server chunk load not caused by
 * the player chunk map, aggregated by the first frame outside Minecraft/Forge, with sample coords.
 */
public final class ChunkLoadTracer {

    private static final int SAMPLES = 5;

    private static final class Bucket {
        int count;
        final List<String> samples = new ArrayList<String>();
    }

    private static long endAt;
    private static long started;
    private static String requester;
    private static int skipped;
    private static final Map<String, Bucket> BUCKETS = new HashMap<String, Bucket>();

    public static boolean start(ICommandSender sender, int seconds) {
        if (endAt != 0) return false;
        BUCKETS.clear();
        skipped = 0;
        started = System.currentTimeMillis();
        endAt = started + seconds * 1000L;
        requester = sender instanceof EntityPlayerMP ? sender.getName() : null;
        DregoraPatches.LOGGER.info("chunkloads: tracing for {} s", seconds);
        return true;
    }

    public static boolean isRunning() {
        return endAt != 0;
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load e) {
        if (endAt == 0 || e.getWorld().isRemote) return;
        String key = StackClassifier.classify(Thread.currentThread().getStackTrace());
        if (key == null) {
            skipped++;
            return;
        }
        Bucket b = BUCKETS.get(key);
        if (b == null) BUCKETS.put(key, b = new Bucket());
        b.count++;
        if (b.samples.size() < SAMPLES) {
            b.samples.add("dim " + e.getWorld().provider.getDimension() + " [" + e.getChunk().x + ", " + e.getChunk().z + "]");
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || endAt == 0 || System.currentTimeMillis() < endAt) return;
        endAt = 0;
        List<String> lines = report();
        for (String l : lines) DregoraPatches.LOGGER.info("chunkloads: {}", l);
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        EntityPlayerMP p = requester == null || server == null ? null : server.getPlayerList().getPlayerByUsername(requester);
        if (p != null) {
            for (String l : lines) p.sendMessage(new TextComponentString(l));
        }
        BUCKETS.clear();
    }

    private static List<String> report() {
        List<Map.Entry<String, Bucket>> entries = new ArrayList<Map.Entry<String, Bucket>>(BUCKETS.entrySet());
        Collections.sort(entries, (a, b) -> Integer.compare(b.getValue().count, a.getValue().count));
        int total = 0;
        for (Map.Entry<String, Bucket> en : entries) total += en.getValue().count;
        List<String> out = new ArrayList<String>();
        out.add(TextFormatting.GOLD + "chunkloads" + TextFormatting.RESET + " over "
                + (System.currentTimeMillis() - started) / 1000 + " s: " + total + " non-player load(s), "
                + skipped + " player/async load(s) skipped");
        int shown = 0;
        for (Map.Entry<String, Bucket> en : entries) {
            if (shown++ >= 15) {
                out.add(" ... " + (entries.size() - 15) + " more (see log)");
                break;
            }
            out.add(" " + en.getValue().count + "  " + en.getKey() + "  " + TextFormatting.GRAY + en.getValue().samples);
        }
        if (entries.size() > 15) {
            for (Map.Entry<String, Bucket> en : entries.subList(15, entries.size())) {
                DregoraPatches.LOGGER.info("chunkloads:  {}  {}  {}", en.getValue().count, en.getKey(), en.getValue().samples);
            }
        }
        return out;
    }
}
