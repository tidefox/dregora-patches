package io.github.tidefox.dregorapatches.mixinsupport;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records which mixin configs were loaded by Mixin and which of our mixins were applied,
 * so runtime code can degrade gracefully and {@code /dpatch} can report it.
 */
public final class MixinState {

    public static final String MIXIN_PACKAGE = "io.github.tidefox.dregorapatches.mixin.";

    private static final Set<String> LOADED_PACKAGES = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final ConcurrentHashMap<String, Set<String>> APPLIED = new ConcurrentHashMap<String, Set<String>>();

    private MixinState() {}

    /** Called from the config plugin's onLoad with the config's mixin package. */
    public static void markLoaded(String mixinPackage) {
        LOADED_PACKAGES.add(mixinPackage);
    }

    public static void markApplied(String config, String mixinClassName) {
        Set<String> set = APPLIED.get(config);
        if (set == null) {
            set = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
            Set<String> prev = APPLIED.putIfAbsent(config, set);
            if (prev != null) set = prev;
        }
        set.add(mixinClassName);
    }

    /** @param name mixin class name relative to {@link #MIXIN_PACKAGE}, e.g. {@code "vanilla.MixinWorld"}. */
    public static boolean isApplied(String name) {
        String full = MIXIN_PACKAGE + name;
        for (Set<String> set : APPLIED.values()) {
            if (set.contains(full)) return true;
        }
        return false;
    }

    /** True if Mixin loaded the config whose mixin package is {@code MIXIN_PACKAGE + sub}. */
    public static boolean isConfigLoaded(String sub) {
        return LOADED_PACKAGES.contains(MIXIN_PACKAGE + sub) || LOADED_PACKAGES.contains(MIXIN_PACKAGE + sub + ".");
    }

    /** Snapshot: config name → applied mixin class names, both sorted. */
    public static SortedMap<String, Set<String>> snapshot() {
        SortedMap<String, Set<String>> out = new TreeMap<String, Set<String>>();
        for (Map.Entry<String, Set<String>> e : APPLIED.entrySet()) {
            out.put(e.getKey(), Collections.unmodifiableSet(new TreeSet<String>(e.getValue())));
        }
        return out;
    }

    /** Test hook. */
    static void reset() {
        LOADED_PACKAGES.clear();
        APPLIED.clear();
    }
}
