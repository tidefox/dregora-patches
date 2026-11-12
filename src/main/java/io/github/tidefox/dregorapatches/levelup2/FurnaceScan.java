package io.github.tidefox.dregorapatches.levelup2;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.github.tidefox.dregorapatches.util.Ema;
import levelup2.api.IProcessor;
import levelup2.capability.PlayerCapability;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBrewingStand;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Replacement for Level Up! 2's {@code FurnaceEfficiencyBonus.doFurnaceTicks} (docs/08 §2).
 * Same guard, same capability calls, same collect-then-process order; only tile entities
 * whose class can carry the capability are asked. Every {@link #LEARN_INTERVAL} calls the
 * original filter runs over every tile entity once to learn classes we did not expect.
 * While {@code loadedTileEntityList} is structurally unchanged ({@link ListVersion}), the candidate
 * tile entities found last time are reused instead of walking the whole list (docs/08 §5).
 * Only loaded when the Level Up! 2 mixin applied (references Level Up! 2 classes).
 */
public final class FurnaceScan {

    public static final int LEARN_INTERVAL = 200;

    private static final CandidateCache CACHE = new CandidateCache(TileEntityFurnace.class, TileEntityBrewingStand.class);
    private static final List<IProcessor> SCRATCH = new ArrayList<IProcessor>();

    /** Per dimension: the candidates found in {@code world}'s list at {@code version}. */
    private static final Map<Integer, Snapshot> SNAPSHOTS = new HashMap<Integer, Snapshot>();

    private static int calls;
    private static long rebuilds;
    private static long cmes;
    private static long learnScans;
    private static final Ema NANOS = new Ema(0.01);
    private static final Ema CANDIDATES = new Ema(0.01);
    private static final Ema PROCESSED = new Ema(0.01);

    private FurnaceScan() {}

    public static void tick(TickEvent.WorldTickEvent evt) {
        World world = evt.world;
        // Same guard as the original.
        if (world.isRemote || evt.phase != TickEvent.Phase.END || evt.side != Side.SERVER) return;
        Capability<IProcessor> cap = PlayerCapability.MACHINE_PROCESSING;
        long t0 = System.nanoTime();
        boolean learn = ++calls % LEARN_INTERVAL == 0;
        if (learn) learnScans++;
        List<IProcessor> procs = SCRATCH;
        int candidates = 0;
        int processed = 0;
        try {
            try {
                List<TileEntity> list = world.loadedTileEntityList;
                synchronized (list) {
                    dropStale();
                    int dim = world.provider.getDimension();
                    long version = ListVersion.of(list);
                    Snapshot snap = SNAPSHOTS.get(dim);
                    List<TileEntity> scan;
                    if (!learn && version != ListVersion.UNKNOWN && snap != null && snap.world == world && snap.version == version) {
                        scan = snap.candidates;
                    } else {
                        // Learning checks everything (and may add classes); otherwise filter by class and remember.
                        scan = learn ? list : filter(list);
                        if (learn || version == ListVersion.UNKNOWN) SNAPSHOTS.remove(dim);
                        else SNAPSHOTS.put(dim, new Snapshot(world, version, scan));
                        rebuilds++;
                    }
                    for (int i = 0; i < scan.size(); i++) {
                        TileEntity te = scan.get(i);
                        if (te == null) continue;
                        candidates++;
                        if (te.hasCapability(cap, EnumFacing.UP)) {
                            if (learn) CACHE.learn(te.getClass());
                            procs.add(te.getCapability(cap, EnumFacing.UP));
                        }
                    }
                }
            } catch (ConcurrentModificationException e) {
                // The original swallows this silently too (and processes nothing that tick).
                cmes++;
                procs.clear();
            }
            for (int i = 0; i < procs.size(); i++) {
                IProcessor p = procs.get(i);
                if (p == null) continue;
                EntityPlayer owner = p.getPlayerFromUUID();
                if (owner != null) {
                    p.extraProcessing(owner);
                    processed++;
                }
            }
        } finally {
            procs.clear();
        }
        if (!learn) CANDIDATES.add(candidates);
        PROCESSED.add(processed);
        NANOS.add(System.nanoTime() - t0);
    }

    private static List<TileEntity> filter(List<TileEntity> list) {
        List<TileEntity> out = new ArrayList<TileEntity>();
        for (int i = 0; i < list.size(); i++) {
            TileEntity te = list.get(i);
            if (te != null && CACHE.isCandidate(te.getClass())) out.add(te);
        }
        return out;
    }

    /** Forget snapshots of unloaded/replaced worlds so their tile entities (and the world) can be collected. */
    private static void dropStale() {
        for (Iterator<Map.Entry<Integer, Snapshot>> it = SNAPSHOTS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Snapshot> e = it.next();
            if (DimensionManager.getWorld(e.getKey()) != e.getValue().world) it.remove();
        }
    }

    private static final class Snapshot {
        final World world;
        final long version;
        final List<TileEntity> candidates;

        Snapshot(World world, long version, List<TileEntity> candidates) {
            this.world = world;
            this.version = version;
            this.candidates = candidates;
        }
    }

    public static List<String> stats() {
        List<String> out = new ArrayList<String>();
        out.add(String.format("candidate classes: %d (%d learned outside furnace/brewing stand)",
                CACHE.candidateClasses(), CACHE.learnedClasses()));
        out.add(String.format("per world tick (avg): %.1f candidates checked, %.2f owned machines processed, %.3f ms",
                CANDIDATES.get(), PROCESSED.get(), NANOS.get() / 1e6));
        out.add(String.format("calls: %d, full list walks: %d, learning scans: %d, CMEs swallowed: %d",
                calls, rebuilds, learnScans, cmes));
        return out;
    }
}
