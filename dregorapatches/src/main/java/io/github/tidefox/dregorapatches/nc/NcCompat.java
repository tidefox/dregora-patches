package io.github.tidefox.dregorapatches.nc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.tidefox.dregorapatches.DregoraPatches;
import io.github.tidefox.dregorapatches.reactor.Claim;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.Loader;

/** The only place that touches NuclearCraft classes outside mixins; every call checks NC is loaded. */
public final class NcCompat {

    /** NC's default fission_max_size (24) + 2, used when NC is absent. */
    private static final int DEFAULT_RADIUS = 26;

    private static Boolean loaded;

    private NcCompat() {}

    public static boolean isLoaded() {
        if (loaded == null) loaded = Loader.isModLoaded("nuclearcraft");
        return loaded;
    }

    /** Claim box half-size: fission_max_size + 2 (docs/07 §3 step 2). */
    public static int boxRadius() {
        if (!isLoaded()) return DEFAULT_RADIUS;
        try {
            return Access.maxSize() + 2;
        } catch (LinkageError e) {
            return DEFAULT_RADIUS;
        }
    }

    private static boolean snapshotFailed;

    /**
     * Copies the controller's public status fields into the claim. Returns false only if {@code te}
     * is not a controller; a failed read keeps the old status (logged once) and still returns true.
     */
    public static boolean snapshot(TileEntity te, Claim c) {
        if (!isLoaded() || !FrozenTypes.isController(te)) return false;
        try {
            Access.snapshot(te, c);
        } catch (LinkageError | RuntimeException | ReflectiveOperationException e) {
            if (!snapshotFailed) {
                snapshotFailed = true;
                DregoraPatches.LOGGER.warn("Could not read fission controller status; /reactor list shows no status", e);
            }
        }
        return true;
    }

    /** "gui.container.fission_controller.casing_incomplete" → "casing incomplete". */
    public static String problemText(String problem) {
        if (problem == null || problem.isEmpty()) return "";
        String s = problem;
        int dot = s.lastIndexOf('.');
        if (dot >= 0) s = s.substring(dot + 1);
        return s.replace('_', ' ');
    }

    /**
     * NCConfig is read directly. TileFissionController is read through cached reflection: its
     * class implements OpenComputers/IC2 API interfaces that are not on our compile classpath.
     * Snapshots run every 100 ticks for a handful of claims, so the cost is irrelevant.
     */
    private static final class Access {
        private static Field complete, heat, problem, energy;
        private static Method isProcessing, getMaxHeat;

        static int maxSize() {
            return nc.config.NCConfig.fission_max_size;
        }

        static void snapshot(TileEntity te, Claim c) throws ReflectiveOperationException {
            if (complete == null) {
                Class<?> k = Class.forName(FrozenTypes.CONTROLLER, false, NcCompat.class.getClassLoader());
                heat = k.getField("heat");
                problem = k.getField("problem");
                energy = k.getField("currentEnergyStored");
                isProcessing = k.getMethod("isProcessing");
                getMaxHeat = k.getMethod("getMaxHeat");
                complete = k.getField("complete");
            }
            c.complete = complete.getInt(te) == 1;
            c.processing = (Boolean) isProcessing.invoke(te);
            Object p = problem.get(te);
            c.problem = p == null ? "" : p.toString();
            int max = (Integer) getMaxHeat.invoke(te);
            c.heatPercent = max <= 0 ? 0 : (int) Math.round(100.0 * heat.getDouble(te) / max);
            c.energy = energy.getInt(te);
            c.statusTime = System.currentTimeMillis();
        }
    }
}
