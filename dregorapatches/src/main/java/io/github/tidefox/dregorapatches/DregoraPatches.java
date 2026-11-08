package io.github.tidefox.dregorapatches;

import java.util.Map;
import java.util.Set;

import io.github.tidefox.dregorapatches.cmd.CmdDpatch;
import io.github.tidefox.dregorapatches.core.DregoraPatchesPlugin;
import io.github.tidefox.dregorapatches.mixinsupport.MixinState;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Server-side fixes for RLCraft Dregora pack mods. Clients need nothing. */
@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION,
        acceptableRemoteVersions = "*", acceptedMinecraftVersions = "[1.12.2]",
        dependencies = "after:nuclearcraft;after:levelup2")
public class DregoraPatches {

    public static final Logger LOGGER = LogManager.getLogger("dregorapatches");

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        if (!DregoraPatchesPlugin.isEnqueued()) {
            LOGGER.error("Mixin configs were never enqueued (FermiumBooter missing?). No patches are active.");
        }
        int applied = 0;
        for (Map.Entry<String, Set<String>> e : MixinState.snapshot().entrySet()) {
            applied += e.getValue().size();
        }
        LOGGER.info("Dregora Patches {} ready: {} mixin(s) applied", Tags.VERSION, applied);
        event.registerServerCommand(new CmdDpatch());
    }
}
