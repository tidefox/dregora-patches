package io.github.tidefox.warband.core;

import io.github.tidefox.warband.Tags;
import net.minecraftforge.common.config.Config;

/**
 * {@code config/warband.cfg}. One category per module (docs/03..06). Values are read at
 * event time, so {@code /wb reload} applies them without a restart.
 */
@Config(modid = Tags.MOD_ID)
public final class WarbandConfig {

    @Config.Comment("Private death recap (docs/05)")
    public static final Recap recap = new Recap();

    @Config.Comment("Trick shots on announced mobs (docs/06)")
    public static final TrickShots trickshots = new TrickShots();

    @Config.Comment("Kill feed (docs/03)")
    public static final KillFeed killfeed = new KillFeed();

    @Config.Comment("Adaptive friendly fire (docs/04). Changes gameplay; off by default.")
    public static final FriendlyFire ff = new FriendlyFire();

    public enum DeliverOn { RESPAWN, DEATH, BOTH }

    public enum Scope { GLOBAL, DIMENSION, RADIUS }

    public static final class Recap {
        public boolean enabled = true;

        @Config.Comment("Only hits in the last N seconds before death are shown")
        @Config.RangeInt(min = 1, max = 120)
        public int windowSec = 10;

        @Config.Comment("Hits kept per player (ring buffer)")
        @Config.RangeInt(min = 1, max = 100)
        public int maxHits = 20;

        @Config.Comment("Damage sources listed in the collapsed message; the rest are summed into one line")
        @Config.RangeInt(min = 1, max = 20)
        public int maxSources = 5;

        @Config.Comment("When the dead player gets the recap. The death screen may hide chat, so RESPAWN is the default.")
        public DeliverOn deliverOn = DeliverOn.RESPAWN;

        @Config.Comment("Show a [share] button that posts the recap to everyone (once per death)")
        public boolean allowShare = true;

        @Config.Comment("Include heals in the timeline")
        public boolean showHealing = false;

        @Config.Comment("Show the [timeline] hover with every hit")
        public boolean showTimeline = true;
    }

    public static final class TrickShots {
        public boolean enabled = true;

        @Config.Comment("Shortest arrow kill (blocks, origin to victim) that counts")
        @Config.RangeDouble(min = 1, max = 1024)
        public double minDistance = 40;

        @Config.Comment("Tiers as name=distance, any order. Highest reached tier names the shot.")
        public String[] tiers = {"Nice shot=40", "Long shot=64", "Sniper=96", "Legendary=128"};

        @Config.Comment("Registry ids that always count as announced, e.g. iceandfire:icedragon")
        public String[] mobs = {};

        @Config.Comment({"Entity NBT markers that make a mob announced. Each entry: a key or a path with '/'",
                "(matched at top level, under ForgeData and under ForgeCaps), optionally =value or !=value.",
                "Syntax examples: SomeKey, ForgeCaps/modid:cap/tier!=0, ForgeData/Variant=boss. Fill from /wb debug kill."})
        public String[] nbtMarkers = {};

        @Config.Comment("Only announce kills of announced mobs (bosses, named, mobs[], nbtMarkers[])")
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
        public boolean enabled = true;
    }

    public static final class FriendlyFire {
        public boolean enabled = false;
    }

    private WarbandConfig() {}
}
