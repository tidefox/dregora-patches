package io.github.tidefox.dregorapatches;

import java.util.Map;
import java.util.Set;

import io.github.tidefox.dregorapatches.cmd.CmdDpatch;
import io.github.tidefox.dregorapatches.combat.DebugProbe;
import io.github.tidefox.dregorapatches.combat.ProjectileOrigin;
import io.github.tidefox.dregorapatches.core.DregoraPatchesPlugin;
import io.github.tidefox.dregorapatches.diag.ChunkLoadTracer;
import io.github.tidefox.dregorapatches.killfeed.KillFeedListener;
import io.github.tidefox.dregorapatches.mixinsupport.MixinState;
import io.github.tidefox.dregorapatches.reactor.CmdReactor;
import io.github.tidefox.dregorapatches.reactor.ReactorChunkLoader;
import io.github.tidefox.dregorapatches.reactor.ReactorClaims;
import io.github.tidefox.dregorapatches.reactor.ReactorEvents;
import io.github.tidefox.dregorapatches.trick.AnnouncedMobs;
import io.github.tidefox.dregorapatches.trick.CmdTrickShots;
import io.github.tidefox.dregorapatches.trick.TrickShotListener;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Server-side fixes and co-op features for the RLCraft Dregora server: NuclearCraft and
 * Level Up! 2 patches (mixins), trick shots and kill feed (plain Forge event observers).
 * Clients need nothing.
 */
@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION,
        acceptableRemoteVersions = "*", acceptedMinecraftVersions = "[1.12.2]",
        dependencies = "after:nuclearcraft;after:levelup2")
public class DregoraPatches {

    public static final Logger LOGGER = LogManager.getLogger("dregorapatches");

    /** Periodic stats line every 5 min when nc.logStats is on. */
    private static final int LOG_INTERVAL = 6000;

    @Mod.Instance(Tags.MOD_ID)
    public static DregoraPatches instance;

    private int ticks;

    private final KillFeedListener killFeed = new KillFeedListener();

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new ReactorEvents());
        MinecraftForge.EVENT_BUS.register(new ChunkLoadTracer());
        MinecraftForge.EVENT_BUS.register(this);
        // Observer features: each checks its config switch per event, so /dpatch reload toggles them.
        MinecraftForge.EVENT_BUS.register(new ProjectileOrigin());
        MinecraftForge.EVENT_BUS.register(DebugProbe.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new TrickShotListener());
        MinecraftForge.EVENT_BUS.register(killFeed);
        ForgeChunkManager.setForcedChunkLoadingCallback(this, new ReactorChunkLoader());
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        if (!DregoraPatchesPlugin.isEnqueued()) {
            LOGGER.error("Mixin configs were never enqueued (FermiumBooter missing?). No patches are active.");
        }
        int applied = 0;
        for (Map.Entry<String, Set<String>> e : MixinState.snapshot().entrySet()) {
            applied += e.getValue().size();
        }
        LOGGER.info("Dregora Patches {} ready: {} mixin(s) applied; freezeReactorParts={}, chunkGuard={}, furnaceScanFix={}",
                Tags.VERSION, applied, PatchesConfig.nc.freezeReactorParts, PatchesConfig.nc.chunkGuard,
                PatchesConfig.levelup2.furnaceScanFix);
        ReactorClaims.load(event.getServer().getEntityWorld());
        event.registerServerCommand(new CmdDpatch());
        event.registerServerCommand(new CmdReactor());
        event.registerServerCommand(new CmdTrickShots());
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        ReactorClaims.unload();
        ReactorChunkLoader.clear();
        AnnouncedMobs.clearCache();
        killFeed.clear();
        DebugProbe.INSTANCE.off();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !PatchesConfig.nc.logStats || ++ticks % LOG_INTERVAL != 0) return;
        for (String l : CmdDpatch.ncStats(FMLCommonHandler.instance().getMinecraftServerInstance())) {
            LOGGER.info("nc stats: {}", net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(l));
        }
    }
}
