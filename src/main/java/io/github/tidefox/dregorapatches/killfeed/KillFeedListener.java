package io.github.tidefox.dregorapatches.killfeed;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import io.github.tidefox.dregorapatches.PatchesConfig;
import io.github.tidefox.dregorapatches.combat.ShotTag;
import io.github.tidefox.dregorapatches.trick.AnnouncedMobs;
import io.github.tidefox.dregorapatches.util.Chat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Kill feed (docs/03): one follow-up line under the death message that is already in chat,
 * for a player killing a player and for a player killing an announced mob:
 * <pre>
 * Blight Zombie was slain by Alice            ← the pack's / vanilla's own line, untouched
 *   ↳ with [Longbow] · 97m · final 22.0 · 1m12s
 * </pre>
 * Observer only. {@code LivingDeathEvent} fires before vanilla (and Scaling Health) send their
 * line, so the follow-up is queued and sent at the end of the server tick.
 */
public final class KillFeedListener {

    private final Map<EntityLivingBase, Fight> fights = new WeakHashMap<>();
    private final List<ITextComponent> queue = new ArrayList<>();

    // ---- fight tracking ----------------------------------------------------------------

    /** Mob victims: post-armor amount. Doesn't fire for players (First Aid cancels their hurt at LOWEST). */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public void onDamage(LivingDamageEvent e) {
        EntityLivingBase v = e.getEntityLiving();
        if (!(v instanceof EntityPlayer)) {
            record(v, e.getSource(), e.getAmount());
        }
    }

    /** Player victims: read at LOW, before First Aid cancels the hurt (docs/00 observer rule 5). */
    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = false)
    public void onPlayerHurt(LivingHurtEvent e) {
        EntityLivingBase v = e.getEntityLiving();
        if (v instanceof EntityPlayerMP) {
            record(v, e.getSource(), e.getAmount());
        }
    }

    private void record(EntityLivingBase victim, DamageSource src, float amount) {
        PatchesConfig.KillFeed c = PatchesConfig.killfeed;
        // Cheap checks first: this runs for every hit on every entity.
        if (!c.enabled || victim.world.isRemote || KillCredit.killer(victim, src) == null) {
            return;
        }
        if (!(victim instanceof EntityPlayer) && (!c.announcedMobKills || AnnouncedMobs.cached(victim) == null)) {
            return;
        }
        Fight f = fights.get(victim);
        if (f == null) {
            f = new Fight();
            fights.put(victim, f);
        }
        f.hit(victim.world.getTotalWorldTime(), amount, timeoutTicks());
    }

    private static long timeoutTicks() {
        return PatchesConfig.killfeed.fightTimeoutSec * 20L;
    }

    // ---- death -------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public void onDeath(LivingDeathEvent e) {
        EntityLivingBase victim = e.getEntityLiving();
        Fight fight = fights.remove(victim);
        PatchesConfig.KillFeed c = PatchesConfig.killfeed;
        if (!c.enabled || victim.world.isRemote) {
            return;
        }
        EntityPlayerMP killer = KillCredit.killer(victim, e.getSource());
        if (killer == null) {
            return;
        }
        if (victim instanceof EntityPlayer) {
            // No vanilla line to follow when death messages are off.
            if (!c.playerKills || !victim.world.getGameRules().getBoolean("showDeathMessages")) {
                return;
            }
        } else {
            boolean announced = c.announcedMobKills && AnnouncedMobs.cached(victim) != null;
            AnnouncedMobs.forget(victim);
            if (!announced) {
                return;
            }
        }
        long now = victim.world.getTotalWorldTime();
        Entity direct = e.getSource().getImmediateSource();
        ShotTag shot = ShotTag.read(direct);
        if (shot != null && !shot.shooter.equals(killer.getCachedUniqueIdString())) {
            shot = null; // deflected arrow: not this player's shot
        }
        ItemStack weapon = shot != null ? shot.launcher : killer.getHeldItemMainhand();
        double distance = shot != null && c.showDistance ? shot.distanceTo(victim) : Double.NaN;
        double finalHit = fight != null && c.showFinalHit ? fight.finalHit(now) : Double.NaN;
        long duration = fight != null && c.showDuration ? fight.duration(now, timeoutTicks()) : -1;
        String tail = KillLine.tail(distance, finalHit, duration);
        queue.add(Chat.fit(line(weapon, tail, true), line(weapon, tail, false)));
    }

    static ITextComponent line(@Nullable ItemStack weapon, String tail, boolean hovers) {
        TextComponentString root = new TextComponentString("");
        root.appendSibling(Chat.text("  ↳ ", TextFormatting.GRAY));
        if (weapon == null || weapon.isEmpty()) {
            root.appendSibling(Chat.text("bare-handed", TextFormatting.GRAY));
        } else {
            root.appendSibling(Chat.text("with ", TextFormatting.GRAY));
            root.appendSibling(hovers ? Chat.item(weapon, TextFormatting.AQUA)
                    : Chat.item(weapon.getDisplayName(), null, TextFormatting.AQUA));
        }
        if (!tail.isEmpty()) {
            root.appendSibling(Chat.text(tail, TextFormatting.GRAY));
        }
        return root;
    }

    /** After every listener and vanilla's death message have run. */
    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || queue.isEmpty()) {
            return;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null) {
            for (ITextComponent msg : queue) {
                Chat.broadcast(server, msg);
            }
        }
        queue.clear();
    }

    public void clear() {
        fights.clear();
        queue.clear();
    }
}
