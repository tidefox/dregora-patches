package io.github.tidefox.dregorapatches;

import net.minecraftforge.common.config.Config;

/**
 * {@code config/dregorapatches.cfg}. Keys follow docs/07 §3 and docs/08.
 */
@Config(modid = Tags.MOD_ID)
public final class PatchesConfig {

    @Config.Comment("NuclearCraft ruin fix (docs/07)")
    public static final Nc nc = new Nc();

    @Config.Comment("Reactor claims (docs/07)")
    public static final Reactor reactor = new Reactor();

    @Config.Comment("Level Up! 2 (docs/08)")
    public static final LevelUp2 levelup2 = new LevelUp2();

    public static final class Nc {
        @Config.Comment("Fission controllers, ports and active coolers only tick while their reactor is claimed (/reactor). "
                + "false = every reactor ticks as in vanilla NuclearCraft.")
        public boolean freezeReactorParts = true;

        @Config.Comment("While NuclearCraft code runs, it can no longer load chunks or keep them loaded. "
                + "Turn on only after measuring freezeReactorParts.")
        public boolean chunkGuard = false;

        @Config.Comment("Log a periodic stats line.")
        public boolean logStats = false;
    }

    public static final class Reactor {
        @Config.Comment("Maximum claimed reactors per player. 0 = unlimited.")
        @Config.RangeInt(min = 0)
        public int maxClaimsPerPlayer = 0;

        @Config.Comment("X/Z shown to non-ops (without -exact) are rounded to this many blocks.")
        @Config.RangeInt(min = 1, max = 10000)
        public int coordEstimate = 100;

        @Config.Comment("Allow the op-only '/reactor admin keeploaded' command.")
        public boolean allowKeepLoaded = true;
    }

    public static final class LevelUp2 {
        @Config.Comment("Only check furnaces/brewing stands instead of every loaded tile entity each tick (same gameplay).")
        public boolean furnaceScanFix = true;
    }

    private PatchesConfig() {}

}
