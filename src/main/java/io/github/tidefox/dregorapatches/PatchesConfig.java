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

    @Config.Comment("Lycanites Mobs pets (docs/09)")
    public static final Lycanites lycanites = new Lycanites();

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

    public static final class Lycanites {
        @Config.Comment("Teleport in the summoning menu also works for a pet/mount whose chunk was unloaded: "
                + "it is respawned at you with its level, XP, inventory and health.")
        public boolean petRecallFix = true;
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
        @Config.Comment({"One line per kill (docs/03 §2.6): the kill line already in chat (vanilla, Scaling Health,",
                "Champions, any mod) is replaced by vanilla's death message with badges, the weapon and one extra:",
                "  Blight Xaphan Lvl 4 was shot by Alice using [icon] · 97m"})
        public boolean enabled = true;

        @Config.Comment("Player killed by a player (needs gamerule showDeathMessages)")
        public boolean playerKills = true;

        @Config.Comment({"Notable mobs killed by a player get our line even when nothing else announced them.",
                "Notable = boss, Blight, announceMobs, trickshots.mobs/nbtMarkers, elite badge, announceMinMaxHealth,",
                "a trick shot (and named mobs with announceNamed)."})
        public boolean announceUnannounced = true;

        @Config.Comment("Registry ids that are notable and never collapsed into a streak (also count for trick shots). /dpatch debug kill prints a mob's id.")
        public String[] announceMobs = {"iceandfire:seaserpent", "iceandfire:if_hydra", "iceandfire:dread_lich",
                "iceandfire:myrmex_queen", "srparasites:anc_dreadnaut", "srparasites:anc_overlord",
                "srparasites:overseer", "srparasites:warden", "srparasites:marauder", "srparasites:monarch",
                "srparasites:beckon_siv", "srparasites:beckon_sv", "srparasites:dispatcher_siv"};

        @Config.Comment("Any mob with at least this max health is notable. 0 = off.")
        @Config.RangeDouble(min = 0, max = 1000000)
        public double announceMinMaxHealth = 150;

        @Config.Comment("Mobs with a custom name are notable (off: Lycanites/name-tagged mobs would spam chat)")
        public boolean announceNamed = false;

        @Config.Comment("Champions tier (★ count) from which a Champion is elite (notable, never collapsed). 0 = off.")
        @Config.RangeInt(min = 0, max = 100)
        public int championsMinTier = 3;

        @Config.Comment("InfernalMobs modifier count from which an Infernal is elite (6 = Ultra, 11 = Infernal). 0 = off.")
        @Config.RangeInt(min = 0, max = 100)
        public int infernalMinModifiers = 6;

        @Config.Comment("Ice and Fire dragon ids; they get a 'Stage N' badge (stage from AgeTicks)")
        public String[] dragonMobs = {"iceandfire:firedragon", "iceandfire:icedragon", "iceandfire:lightningdragon"};

        @Config.Comment("Dragon stage from which a dragon is elite. 0 = off.")
        @Config.RangeInt(min = 0, max = 5)
        public int dragonMinStage = 3;

        @Config.Comment("SRParasites evolution phase (0-8, the phase a parasite was born in) from which it is elite. 0 = off.")
        @Config.RangeInt(min = 0, max = 100)
        public int srpMinPhase = 6;

        @Config.Comment("Kills by a player's tamed mob (wolf, dragon, …) credit the owner")
        public boolean petKills = true;

        @Config.Comment("A death within this many seconds of a player's last hit (fire, bleeding, fall) credits that player. 0 = off.")
        @Config.RangeInt(min = 0, max = 60)
        public int lingerCreditSec = 10;

        @Config.Comment("Ticks to wait for another mod's kill line before deciding nothing announced the kill")
        @Config.RangeInt(min = 0, max = 100)
        public int lineWaitTicks = 2;

        @Config.Comment({"Same player killing the same non-elite mob type again within this many seconds: no new line,",
                "one summary line when the streak ends ('…and 11 more Defiled Slime kills (4 Blight) in 18s'). 0 = off."})
        @Config.RangeInt(min = 0, max = 300)
        public int streakSec = 10;

        @Config.Comment("Fight time is shown after the weapon from this many seconds (shorter: only in the hover)")
        @Config.RangeInt(min = 0, max = 3600)
        public int minFightSec = 5;

        @Config.Comment("A fight ends after this many seconds without a player hit")
        @Config.RangeInt(min = 1, max = 3600)
        public int fightTimeoutSec = 60;
    }

    private PatchesConfig() {}

}
