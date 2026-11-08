package io.github.tidefox.warband;

import io.github.tidefox.warband.core.CmdWarband;
import io.github.tidefox.warband.core.DebugProbe;
import io.github.tidefox.warband.core.ProjectileOrigin;
import io.github.tidefox.warband.recap.CmdRecap;
import io.github.tidefox.warband.recap.RecapModule;
import io.github.tidefox.warband.trick.AnnouncedMobs;
import io.github.tidefox.warband.trick.CmdTrickShots;
import io.github.tidefox.warband.trick.TrickShotListener;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
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
    public void preInit(FMLPreInitializationEvent event) {
        // Modules check their config switch per event, so /wb reload can toggle them.
        MinecraftForge.EVENT_BUS.register(new ProjectileOrigin());
        MinecraftForge.EVENT_BUS.register(DebugProbe.INSTANCE);
        RecapModule.INSTANCE.register();
        MinecraftForge.EVENT_BUS.register(new TrickShotListener());
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CmdWarband());
        event.registerServerCommand(new CmdRecap());
        event.registerServerCommand(new CmdTrickShots());
        LOGGER.info("Warband {} ready", Tags.VERSION);
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        RecapModule.INSTANCE.serverStopped();
        AnnouncedMobs.clearCache();
        DebugProbe.INSTANCE.off();
    }
}
