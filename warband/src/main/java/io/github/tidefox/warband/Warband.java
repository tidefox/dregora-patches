package io.github.tidefox.warband;

import io.github.tidefox.warband.core.CmdWarband;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Co-op QoL for the Dregora server. Server-side only: vanilla/Forge clients without
 * this jar connect normally and all output is vanilla chat components.
 */
@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION,
        acceptableRemoteVersions = "*", acceptedMinecraftVersions = "[1.12.2]")
public class Warband {

    public static final Logger LOGGER = LogManager.getLogger("warband");

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CmdWarband());
        LOGGER.info("Warband {} ready", Tags.VERSION);
    }
}
