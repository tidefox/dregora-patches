package io.github.tidefox.warband.core;

import io.github.tidefox.warband.Tags;
import net.minecraftforge.common.config.Config;

/**
 * {@code config/warband.cfg}. One category per module; options are added with each
 * module (docs/03..06). P0: module switches only.
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

    public static final class Recap {
        public boolean enabled = true;
    }

    public static final class TrickShots {
        public boolean enabled = true;
    }

    public static final class KillFeed {
        public boolean enabled = true;
    }

    public static final class FriendlyFire {
        public boolean enabled = false;
    }

    private WarbandConfig() {}
}
