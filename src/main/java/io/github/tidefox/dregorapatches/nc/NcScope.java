package io.github.tidefox.dregorapatches.nc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import io.github.tidefox.dregorapatches.PatchesConfig;

/**
 * "NuclearCraft code is running" flag for the chunk guard (docs/07 §3 step 3).
 * Entered around every NC tile-entity tick and around {@code RadiationHandler.updateWorldRadiation}.
 * Server thread only: the guard acts only on the thread that entered the scope.
 */
public final class NcScope {

    private static final Class<?> RADIATION = NcScope.class; // marker for the radiation world tick

    private static int depth;
    private static Thread thread;
    private static Class<?> current;

    private static final Map<Class<?>, long[]> BLOCKED_LOADS = new IdentityHashMap<Class<?>, long[]>();
    private static final Map<Class<?>, long[]> KEPT_QUEUED = new IdentityHashMap<Class<?>, long[]>();
    private static long blockedLoads;
    private static long keptQueued;

    private NcScope() {}

    public static void enter(Object tile) {
        if (depth++ == 0) {
            thread = Thread.currentThread();
            current = tile.getClass();
        }
    }

    public static void enterRadiation() {
        if (depth++ == 0) {
            thread = Thread.currentThread();
            current = RADIATION;
        }
    }

    public static void exit() {
        if (depth > 0 && --depth == 0) {
            current = null;
        }
    }

    /** Safety net at ServerTickEvent START, in case an exception skipped an exit. */
    public static void reset() {
        depth = 0;
        current = null;
    }

    /** True if the guard must act now: switch on, inside NC code, on the thread that entered. */
    public static boolean guardActive() {
        return depth > 0 && PatchesConfig.nc.chunkGuard && Thread.currentThread() == thread;
    }

    public static void recordBlockedLoad() {
        blockedLoads++;
        bump(BLOCKED_LOADS);
    }

    public static void recordKeptQueued() {
        keptQueued++;
        bump(KEPT_QUEUED);
    }

    private static void bump(Map<Class<?>, long[]> map) {
        Class<?> c = current;
        if (c == null) return;
        long[] n = map.get(c);
        if (n == null) map.put(c, n = new long[1]);
        n[0]++;
    }

    public static long blockedLoads() {
        return blockedLoads;
    }

    public static long keptQueued() {
        return keptQueued;
    }

    /** "ClassName n" lines, highest first. */
    public static List<String> breakdown(boolean loads) {
        Map<Class<?>, long[]> map = loads ? BLOCKED_LOADS : KEPT_QUEUED;
        List<Map.Entry<Class<?>, long[]>> entries = new ArrayList<Map.Entry<Class<?>, long[]>>(map.entrySet());
        Collections.sort(entries, (a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
        List<String> out = new ArrayList<String>();
        for (Map.Entry<Class<?>, long[]> e : entries) {
            String name = e.getKey() == RADIATION ? "RadiationHandler.updateWorldRadiation" : e.getKey().getSimpleName();
            out.add(name + " " + e.getValue()[0]);
        }
        return out;
    }
}
