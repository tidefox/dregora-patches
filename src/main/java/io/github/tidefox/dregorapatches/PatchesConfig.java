package io.github.tidefox.dregorapatches;

import net.minecraftforge.common.config.Config;

/**
 * {@code config/dregorapatches.cfg}. Keys follow docs/07 §3, docs/08, docs/06 and docs/03.
 * Trick shot and kill feed values are read at event time, so {@code /dpatch reload} applies them.
 */
@Config(modid = Tags.MOD_ID)
public final class PatchesConfig {

    @Config.Comment("NuclearCraft ruin fix (docs/07)")
    public static final Nc nc = new Nc();

    @Config.Comment("Reactor claims (docs/07)")
    public static final Reactor reactor = new Reactor();

    @Config.Comment("Level Up! 2 (docs/08)")
    public static final LevelUp2 levelup2 = new LevelUp2();

    @Config.Comment("Trick shots on announced mobs (docs/06)")
    public static final TrickShots trickshots = new TrickShots();

    @Config.Comment("Kill feed (docs/03)")
    public static final KillFeed killfeed = new KillFeed();

    public enum Scope { GLOBAL, DIMENSION, RADIUS }

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

    public static final class TrickShots {
        public boolean enabled = true;

        @Config.Comment("Shortest arrow kill (blocks, origin to victim) that counts")
        @Config.RangeDouble(min = 1, max = 1024)
        public double minDistance = 40;

        @Config.Comment("Tiers as name=distance, any order. Highest reached tier names the shot.")
        public String[] tiers = {"Nice shot=40", "Long shot=64", "Sniper=96", "Legendary=128"};

        @Config.Comment("Scaling Health Blights count as announced (ForgeData ScalingHealth.IsBlight, the flag Scaling Health itself checks)")
        public boolean blights = true;

        @Config.Comment("Registry ids that always count as announced, e.g. iceandfire:icedragon")
        public String[] mobs = {};

        @Config.Comment({"Entity NBT markers that make a mob announced. Each entry: a key or a path with '/'",
                "(matched at top level, under ForgeData and under ForgeCaps), optionally =value or !=value.",
                "Syntax examples: SomeKey, ForgeCaps/modid:cap/tier!=0, ForgeData/Variant=boss. Fill from /dpatch debug kill."})
        public String[] nbtMarkers = {};

        @Config.Comment("Only announce kills of announced mobs (bosses, Blights, named, mobs[], nbtMarkers[])")
        public boolean requireAnnounced = true;

        @Config.Comment("Shooter-only action bar distance for any qualifying arrow kill that is not announced")
        public boolean privateActionBar = false;

        @Config.Comment("Who sees the callout")
        public Scope scope = Scope.GLOBAL;

        @Config.Comment("Radius for scope = RADIUS (blocks, around the shooter)")
        @Config.RangeInt(min = 1, max = 100000)
        public int radius = 256;

        public boolean showFlightTime = true;

        @Config.Comment("Show airborne / mounted tags")
        public boolean showTags = true;
    }

    public static final class KillFeed {
        @Config.Comment({"One follow-up line under the death message already in chat:",
                "  <victim> was slain by <player>",
                "    -> with [weapon] · distance · final hit · fight time"})
        public boolean enabled = true;

        @Config.Comment("Follow-up for a player killed by a player (needs gamerule showDeathMessages)")
        public boolean playerKills = true;

        @Config.Comment("Follow-up for announced mobs killed by a player: bosses, Blights, named mobs, and the "
                + "trickshots.mobs / trickshots.nbtMarkers lists")
        public boolean announcedMobKills = true;

        @Config.Comment("Shot distance for bow/crossbow kills")
        public boolean showDistance = true;

        @Config.Comment("Damage of the killing hit")
        public boolean showFinalHit = true;

        @Config.Comment("Time since the first player hit (fights under 1 s show none)")
        public boolean showDuration = true;

        @Config.Comment("A fight ends after this many seconds without a player hit")
        @Config.RangeInt(min = 1, max = 3600)
        public int fightTimeoutSec = 60;
    }

    private PatchesConfig() {}

}
