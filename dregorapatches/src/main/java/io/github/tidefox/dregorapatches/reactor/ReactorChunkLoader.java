package io.github.tidefox.dregorapatches.reactor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.tidefox.dregorapatches.DregoraPatches;
import io.github.tidefox.dregorapatches.PatchesConfig;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;

/**
 * Op-only keep-loaded for claimed reactors (docs/07 §3 "Keep loaded"): one ForgeChunkManager
 * ticket per claim, forcing the chunks that overlap its claim box. Re-applied on world load.
 */
public final class ReactorChunkLoader implements ForgeChunkManager.LoadingCallback {

    private static final String KEY = "claimId";
    private static final Map<Integer, Ticket> TICKETS = new HashMap<Integer, Ticket>();

    @Override
    public void ticketsLoaded(List<Ticket> tickets, World world) {
        ReactorClaims.load(world);
        for (Ticket t : tickets) {
            int id = t.getModData().getInteger(KEY);
            Claim c = ReactorClaims.byId(id);
            if (c != null && c.keepLoaded && PatchesConfig.reactor.allowKeepLoaded
                    && c.dim == world.provider.getDimension() && !TICKETS.containsKey(id)) {
                TICKETS.put(id, t);
                force(t, c);
            } else {
                ForgeChunkManager.releaseTicket(t);
            }
        }
    }

    /** WorldEvent.Load: (re)create tickets for keep-loaded claims in that dimension. */
    public static void onWorldLoad(World world) {
        if (!PatchesConfig.reactor.allowKeepLoaded) return;
        int dim = world.provider.getDimension();
        for (Claim c : ReactorClaims.all()) {
            if (c.keepLoaded && c.dim == dim && !TICKETS.containsKey(c.id)) enable(world, c);
        }
    }

    /** Returns a message describing what happened. */
    public static String enable(Claim c) {
        World world = DimensionManager.getWorld(c.dim);
        if (world == null) return "dimension " + c.dim + " is not loaded; the chunks will be kept loaded once it is.";
        return enable(world, c);
    }

    private static String enable(World world, Claim c) {
        Ticket existing = TICKETS.get(c.id);
        if (existing != null) return existing.getChunkList().size() + " chunk(s) already kept loaded.";
        Ticket t = ForgeChunkManager.requestTicket(DregoraPatches.instance, world, ForgeChunkManager.Type.NORMAL);
        if (t == null) {
            DregoraPatches.LOGGER.warn("No chunk-loading ticket available for reactor #{}", c.id);
            return "no chunk-loading ticket available (see config/forgeChunkLoading.cfg).";
        }
        t.getModData().setInteger(KEY, c.id);
        TICKETS.put(c.id, t);
        int n = force(t, c);
        return n + " chunk(s) kept loaded.";
    }

    public static void disable(Claim c) {
        Ticket t = TICKETS.remove(c.id);
        if (t != null) ForgeChunkManager.releaseTicket(t);
    }

    public static boolean isActive(Claim c) {
        return TICKETS.containsKey(c.id);
    }

    public static void clear() {
        TICKETS.clear();
    }

    /** Forces the chunks overlapping the claim box, nearest to the controller first, up to the ticket limit. */
    private static int force(Ticket t, final Claim c) {
        int[] r = ClaimBox.chunkRange(c.pos.getX(), c.pos.getZ(), ReactorClaims.radius());
        List<ChunkPos> chunks = new ArrayList<ChunkPos>();
        for (int cx = r[0]; cx <= r[2]; cx++) {
            for (int cz = r[1]; cz <= r[3]; cz++) {
                chunks.add(new ChunkPos(cx, cz));
            }
        }
        final int ccx = c.pos.getX() >> 4, ccz = c.pos.getZ() >> 4;
        Collections.sort(chunks, (a, b) -> Integer.compare(
                Math.max(Math.abs(a.x - ccx), Math.abs(a.z - ccz)), Math.max(Math.abs(b.x - ccx), Math.abs(b.z - ccz))));
        int max = t.getMaxChunkListDepth();
        int n = 0;
        for (ChunkPos p : chunks) {
            if (n >= max) {
                DregoraPatches.LOGGER.warn("Reactor #{}: claim box needs {} chunks, ticket allows {}", c.id, chunks.size(), max);
                break;
            }
            ForgeChunkManager.forceChunk(t, p);
            n++;
        }
        return n;
    }
}
