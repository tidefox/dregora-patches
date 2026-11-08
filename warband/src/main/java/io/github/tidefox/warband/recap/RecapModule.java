package io.github.tidefox.warband.recap;

import javax.annotation.Nullable;

import io.github.tidefox.warband.core.Chat;
import io.github.tidefox.warband.core.WarbandConfig;
import io.github.tidefox.warband.core.WarbandConfig.DeliverOn;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/** Death recap (docs/05): snapshot on death, private delivery, {@code /recap [share]}. */
public final class RecapModule {

    public static final RecapModule INSTANCE = new RecapModule();

    private final RecapStore store = new RecapStore();
    private final HitRecorder recorder = new HitRecorder(store);

    private RecapModule() {}

    public void register() {
        MinecraftForge.EVENT_BUS.register(recorder);
        MinecraftForge.EVENT_BUS.register(this);
    }

    public void serverStopped() {
        store.clear();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public void onDeath(LivingDeathEvent e) {
        if (!(e.getEntityLiving() instanceof EntityPlayerMP) || !WarbandConfig.recap.enabled) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) e.getEntityLiving();
        recorder.resolve(p.getUniqueID(), true);
        RecapStore.Death d = store.snapshot(p.getUniqueID(), HitRecorder.now(), causeOf(e));
        DeliverOn on = WarbandConfig.recap.deliverOn;
        if (on == DeliverOn.DEATH || on == DeliverOn.BOTH) {
            Chat.send(p, message(d, p.getName(), false));
            d.delivered = on == DeliverOn.DEATH;
        }
    }

    private static String causeOf(LivingDeathEvent e) {
        if (e.getSource().getTrueSource() != null) {
            return e.getSource().getTrueSource().getName();
        }
        return DamageNames.environment(e.getSource().getDamageType(), false);
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) e.player;
        store.clearLive(p.getUniqueID());
        RecapStore.Death d = store.lastDeath(p.getUniqueID());
        if (d == null || d.delivered || e.isEndConquered() || !WarbandConfig.recap.enabled) {
            return;
        }
        d.delivered = true;
        Chat.send(p, message(d, p.getName(), false));
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        store.clearLive(e.player.getUniqueID());
        recorder.forget(e.player.getUniqueID());
    }

    @SubscribeEvent
    public void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        store.clearLive(e.player.getUniqueID());
    }

    @Nullable
    RecapStore.Death lastDeath(EntityPlayerMP p) {
        return store.lastDeath(p.getUniqueID());
    }

    ITextComponent message(RecapStore.Death d, String owner, boolean shared) {
        WarbandConfig.Recap c = WarbandConfig.recap;
        RecapSummary s = RecapSummary.of(d.hits, d.tick, c.windowSec * 20L, c.maxSources);
        boolean share = !shared && c.allowShare && !d.shared && s.killingBlow != null;
        return RecapFormatter.build(s, d, c.windowSec, owner, shared, c.showTimeline, share);
    }
}
