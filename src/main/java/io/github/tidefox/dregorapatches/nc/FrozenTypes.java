package io.github.tidefox.dregorapatches.nc;

import java.util.ArrayList;
import java.util.List;

import io.github.tidefox.dregorapatches.DregoraPatches;

/**
 * NuclearCraft tile entity classes that are frozen unless claimed (docs/07 §3 step 2),
 * resolved by name on first use so a renamed class drops out instead of crashing.
 */
public final class FrozenTypes {

    public static final String CONTROLLER = "nc.tile.generator.TileFissionController";
    public static final String PORT = "nc.tile.dummy.TileFissionPort";
    public static final String ACTIVE_COOLER = "nc.tile.fluid.TileActiveCooler";
    private static final String[] NAMES = {CONTROLLER, PORT, ACTIVE_COOLER};

    private static volatile Class<?>[] types;
    private static Class<?> controller;

    private FrozenTypes() {}

    private static Class<?>[] types() {
        Class<?>[] t = types;
        if (t == null) {
            List<Class<?>> found = new ArrayList<Class<?>>();
            for (String n : NAMES) {
                try {
                    Class<?> c = Class.forName(n, false, FrozenTypes.class.getClassLoader());
                    found.add(c);
                    if (CONTROLLER.equals(n)) controller = c;
                } catch (ClassNotFoundException | LinkageError e) {
                    DregoraPatches.LOGGER.warn("NuclearCraft class {} not found; it will not be frozen", n);
                }
            }
            t = found.toArray(new Class<?>[0]);
            types = t;
        }
        return t;
    }

    public static boolean isFrozenType(Class<?> c) {
        for (Class<?> t : types()) {
            if (t.isAssignableFrom(c)) return true;
        }
        return false;
    }

    /** True if {@code o} is a fission controller tile entity (TileFissionController or a subclass). */
    public static boolean isController(Object o) {
        types();
        return o != null && controller != null && controller.isInstance(o);
    }

    /** Short label for stats: controller / port / active cooler. */
    public static String label(Class<?> c) {
        types();
        if (controller != null && controller.isAssignableFrom(c)) return "controller";
        return c.getSimpleName().contains("Port") ? "port" : c.getSimpleName().contains("Cooler") ? "active cooler" : c.getSimpleName();
    }
}
