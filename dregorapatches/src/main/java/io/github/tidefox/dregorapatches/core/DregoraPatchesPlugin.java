package io.github.tidefox.dregorapatches.core;

import java.util.Map;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Coremod entry point. Its only job is to hand our mixin configs to FermiumBooter:
 * the vanilla config as an EARLY mixin, the mod configs (NuclearCraft, Level Up! 2)
 * as LATE mixins. FermiumBooter adds early configs in its own injectData (SortingIndex
 * 990, after ours) and late configs once mods are on the classpath.
 *
 * We try in the constructor (the documented place). On Forge 2860 + FermiumBooter 1.4.1
 * FermiumBooter's classes are not visible yet at that point (the smoke test sees this with
 * either jar order), so we retry in injectData. By then FermiumBooter's own injectData has
 * usually flushed its early list already, so we register the early config with Mixin
 * ourselves, the same call FermiumBooter makes (see {@link FermiumCompat#addEarlyConfigNow}).
 * Late configs are still read from FermiumBooter's queue. Without FermiumBooter nothing is
 * patched and the server keeps running.
 */
@IFMLLoadingPlugin.Name("DregoraPatches")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(500)
@IFMLLoadingPlugin.TransformerExclusions({"io.github.tidefox.dregorapatches.core."})
public class DregoraPatchesPlugin implements IFMLLoadingPlugin {

    public static final String CONFIG_VANILLA = "mixins.dregorapatches.vanilla.json";
    public static final String CONFIG_NC = "mixins.dregorapatches.nc.json";
    public static final String CONFIG_LEVELUP2 = "mixins.dregorapatches.levelup2.json";

    private static final Logger LOGGER = LogManager.getLogger("dregorapatches");

    private static boolean enqueued = false;

    public DregoraPatchesPlugin() {
        tryEnqueue(false);
    }

    @Override
    public void injectData(Map<String, Object> data) {
        tryEnqueue(true);
    }

    private static void tryEnqueue(boolean lastChance) {
        if (enqueued) return;
        if (!FermiumCompat.isPresent()) {
            if (lastChance) {
                LOGGER.error("FermiumBooter was not found. Dregora Patches' mixins will NOT be applied. "
                        + "Install FermiumBooter (>= 1.4.1) in mods/.");
            }
            return;
        }
        FermiumCompat.enqueueMixin(false, CONFIG_VANILLA);
        FermiumCompat.enqueueMixin(true, CONFIG_NC);
        FermiumCompat.enqueueMixin(true, CONFIG_LEVELUP2);
        if (lastChance) {
            // Constructor ran before FermiumBooter was on the classpath; its injectData may
            // already have flushed the early list. Late configs are still picked up.
            FermiumCompat.addEarlyConfigNow(CONFIG_VANILLA);
        }
        enqueued = true;
        LOGGER.info("Enqueued {} (early{}), {} and {} (late) with FermiumBooter",
                CONFIG_VANILLA, lastChance ? ", added from injectData" : "", CONFIG_NC, CONFIG_LEVELUP2);
    }

    /** True once our configs were handed to FermiumBooter. */
    public static boolean isEnqueued() {
        return enqueued;
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
