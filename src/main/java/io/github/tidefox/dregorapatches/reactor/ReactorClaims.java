package io.github.tidefox.dregorapatches.reactor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import io.github.tidefox.dregorapatches.DregoraPatches;
import io.github.tidefox.dregorapatches.nc.NcCompat;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;

/**
 * Claims, placed-controller records and the claim epoch (docs/07 §3 step 2). Server thread only.
 * Backed by {@link ReactorClaimsData} in the global MapStorage, loaded lazily from any world.
 */
public final class ReactorClaims {

    private static ReactorClaimsData data;
    private static int epoch;
    private static int radius = -1;
    /** Flat copy of claim positions per lookup: {dim, x, y, z} × n. Rebuilt on change. */
    private static int[] index = new int[0];

    public static final TokenStore TOKENS = new TokenStore();

    private ReactorClaims() {}

    /** Bumped on every claim/unclaim (and on load) so cached frozen states re-evaluate. */
    public static int epoch() {
        return epoch;
    }

    public static int radius() {
        if (radius < 0) radius = NcCompat.boxRadius();
        return radius;
    }

    public static void load(World world) {
        if (data != null) return;
        MapStorage storage = world.getMapStorage();
        if (storage == null) return;
        ReactorClaimsData d = (ReactorClaimsData) storage.getOrLoadData(ReactorClaimsData.class, ReactorClaimsData.NAME);
        if (d == null) {
            d = new ReactorClaimsData();
            storage.setData(ReactorClaimsData.NAME, d);
        }
        data = d;
        changed(false);
        DregoraPatches.LOGGER.info("Reactor claims loaded: {} claim(s), {} player-placed controller(s)",
                d.claims.size(), d.placed.size());
    }

    public static void unload() {
        data = null;
        radius = -1;
        index = new int[0];
        TOKENS.clear();
        epoch++;
    }

    public static boolean isLoaded() {
        return data != null;
    }

    private static void changed(boolean dirty) {
        if (data == null) return;
        int[] idx = new int[data.claims.size() * 4];
        int i = 0;
        for (Claim c : data.claims) {
            idx[i++] = c.dim;
            idx[i++] = c.pos.getX();
            idx[i++] = c.pos.getY();
            idx[i++] = c.pos.getZ();
        }
        index = idx;
        epoch++;
        if (dirty) data.markDirty();
    }

    public static void markDirty() {
        if (data != null) data.markDirty();
    }

    /** True if {@code pos} in {@code dim} lies inside some claim box. */
    public static boolean covers(int dim, BlockPos pos) {
        int[] idx = index;
        if (idx.length == 0) return false;
        int r = radius();
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        for (int i = 0; i < idx.length; i += 4) {
            if (idx[i] == dim && ClaimBox.contains(idx[i + 1], idx[i + 2], idx[i + 3], r, x, y, z)) return true;
        }
        return false;
    }

    // ---- placed records

    public static void recordPlaced(int dim, BlockPos pos, UUID placer) {
        if (data == null) return;
        data.placed.put(ReactorClaimsData.key(dim, pos), placer);
        data.markDirty();
    }

    /** Placer UUID, or null if this controller was not placed by a player (worldgen). */
    public static UUID placer(int dim, BlockPos pos) {
        return data == null ? null : data.placed.get(ReactorClaimsData.key(dim, pos));
    }

    public static boolean removePlaced(int dim, BlockPos pos) {
        if (data == null) return false;
        boolean removed = data.placed.remove(ReactorClaimsData.key(dim, pos)) != null;
        if (removed) data.markDirty();
        return removed;
    }

    /** {dim, pos} of every placed record, for the lazy presence check. */
    public static List<Object[]> placedRecords() {
        if (data == null) return Collections.emptyList();
        List<Object[]> out = new ArrayList<Object[]>();
        for (String k : data.placed.keySet()) {
            out.add(new Object[] {ReactorClaimsData.keyDim(k), ReactorClaimsData.keyPos(k)});
        }
        return out;
    }

    // ---- claims

    public static List<Claim> all() {
        return data == null ? Collections.<Claim>emptyList() : Collections.unmodifiableList(new ArrayList<Claim>(data.claims));
    }

    public static List<Claim> ownedBy(UUID owner) {
        List<Claim> out = new ArrayList<Claim>();
        if (data == null) return out;
        for (Claim c : data.claims) {
            if (c.owner.equals(owner)) out.add(c);
        }
        return out;
    }

    public static Claim byId(int id) {
        if (data == null) return null;
        for (Claim c : data.claims) {
            if (c.id == id) return c;
        }
        return null;
    }

    public static Claim at(int dim, BlockPos pos) {
        if (data == null) return null;
        for (Claim c : data.claims) {
            if (c.dim == dim && c.pos.equals(pos)) return c;
        }
        return null;
    }

    public static Claim create(int dim, BlockPos pos, UUID owner, String ownerName) {
        if (data == null) return null;
        Claim c = new Claim(data.nextId++, dim, pos.toImmutable(), owner, ownerName, System.currentTimeMillis());
        data.claims.add(c);
        changed(true);
        return c;
    }

    public static boolean remove(Claim claim) {
        if (data == null) return false;
        for (Iterator<Claim> it = data.claims.iterator(); it.hasNext(); ) {
            if (it.next().id == claim.id) {
                it.remove();
                changed(true);
                return true;
            }
        }
        return false;
    }
}
