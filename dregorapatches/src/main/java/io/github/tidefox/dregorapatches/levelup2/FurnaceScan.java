package io.github.tidefox.dregorapatches.levelup2;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;

import io.github.tidefox.dregorapatches.util.Ema;
import levelup2.api.IProcessor;
import levelup2.capability.PlayerCapability;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBrewingStand;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Replacement for Level Up! 2's {@code FurnaceEfficiencyBonus.doFurnaceTicks} (docs/08 §2).
 * Same guard, same capability calls, same collect-then-process order; only tile entities
 * whose class can carry the capability are asked. Every {@link #LEARN_INTERVAL} calls the
 * original filter runs over every tile entity once to learn classes we did not expect.
 * Only loaded when the Level Up! 2 mixin applied (references Level Up! 2 classes).
 */
public final class FurnaceScan {

    public static final int LEARN_INTERVAL = 200;

    private static final CandidateCache CACHE = new CandidateCache(TileEntityFurnace.class, TileEntityBrewingStand.class);
    private static final List<IProcessor> SCRATCH = new ArrayList<IProcessor>();

    private static int calls;
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
                    for (int i = 0; i < list.size(); i++) {
                        TileEntity te = list.get(i);
                        if (te == null) continue;
                        if (!learn && !CACHE.isCandidate(te.getClass())) continue;
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

    public static List<String> stats() {
        List<String> out = new ArrayList<String>();
        out.add(String.format("candidate classes: %d (%d learned outside furnace/brewing stand)",
                CACHE.candidateClasses(), CACHE.learnedClasses()));
        out.add(String.format("per world tick (avg): %.1f candidates checked, %.2f owned machines processed, %.3f ms",
                CANDIDATES.get(), PROCESSED.get(), NANOS.get() / 1e6));
        out.add(String.format("calls: %d, learning scans: %d, CMEs swallowed: %d", calls, learnScans, cmes));
        return out;
    }
}
