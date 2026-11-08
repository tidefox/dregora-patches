package io.github.tidefox.dregorapatches.core;

import fermiumbooter.FermiumRegistryAPI;
import org.spongepowered.asm.mixin.Mixins;

/**
 * Isolates every FermiumBooter (and FermiumBooter-provided Mixin) reference so this class
 * is only linked once we know FermiumBooter is on the classpath.
 */
final class FermiumCompat {

    private FermiumCompat() {}

    /** Resource lookup, not Class.forName: a miss must never leave a sticky NoClassDefFoundError. */
    static boolean isPresent() {
        ClassLoader cl = DregoraPatchesPlugin.class.getClassLoader();
        return cl != null && cl.getResource("fermiumbooter/FermiumRegistryAPI.class") != null;
    }

    /** API present since FermiumBooter 1.x: {@code enqueueMixin(boolean late, String configuration)}. */
    static void enqueueMixin(boolean late, String config) {
        FermiumRegistryAPI.enqueueMixin(late, config);
    }

    /**
     * Registers an early config with Mixin directly, which is exactly what FermiumBooter's
     * own {@code injectData} does with its early list. Only used when our constructor ran
     * before FermiumBooter's classes were visible, in which case FermiumBooter may already
     * have flushed its early list. Mixin ignores a config name it has already registered,
     * so a second add from FermiumBooter is harmless.
     */
    static void addEarlyConfigNow(String config) {
        Mixins.addConfiguration(config);
    }
}
