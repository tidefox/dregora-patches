package io.github.tidefox.warband.recap;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import io.github.tidefox.warband.core.Chat;
import io.github.tidefox.warband.core.ProjectileOrigin;
import io.github.tidefox.warband.core.WarbandConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Records hits on players (docs/05 §2.1). Observer only.
 *
 * <p>First Aid 1.6.22 handles player damage itself: its {@code LivingHurtEvent} listener runs at
 * LOWEST and <b>cancels every player hurt</b> after applying it to body parts, so for players
 * {@code LivingDamageEvent} never fires, and a LOWEST listener that skips canceled events may
 * never see the hit (same-priority order is registration order). So the hurt is read at
 * {@code LOW} — after every normal-priority mod has adjusted the amount, before First Aid — and
 * the result is resolved from {@code LivingDamageEvent} when it fires, else from the change in
 * health and absorption by the end of the tick (or at death, whichever comes first).
 */
public final class HitRecorder {

    private static final class Pending {
        final long tick;
        final EntityPlayerMP player;
        final DamageSource src;
        final float raw, health, absorption;

        Pending(long tick, EntityPlayerMP player, DamageSource src, float raw) {
            this.tick = tick;
            this.player = player;
            this.src = src;
            this.raw = raw;
            this.health = player.getHealth();
            this.absorption = player.getAbsorptionAmount();
        }
    }

    private final RecapStore store;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public HitRecorder(RecapStore store) {
        this.store = store;
    }

    static long now() {
        return FMLCommonHandler.instance().getMinecraftServerInstance().getTickCounter();
    }

    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = false)
    public void onHurt(LivingHurtEvent e) {
        if (!(e.getEntityLiving() instanceof EntityPlayerMP) || !WarbandConfig.recap.enabled) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) e.getEntityLiving();
        if (e.getAmount() <= 0) {
            return;
        }
        resolve(p.getUniqueID(), false);
        pending.put(p.getUniqueID(), new Pending(now(), p, e.getSource(), e.getAmount()));
    }

    /** Fires only when nothing canceled the hurt (no First Aid, or First Aid ordered after us). */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public void onDamage(LivingDamageEvent e) {
        if (!(e.getEntityLiving() instanceof EntityPlayerMP)) {
            return;
        }
        Pending p = pending.remove(e.getEntityLiving().getUniqueID());
        if (p == null || p.src != e.getSource()) {
            return;
        }
        // Vanilla has already taken absorption off: the event amount is the health loss.
        float absorbed = Math.max(0, p.absorption - p.player.getAbsorptionAmount());
        record(p, Math.max(0, e.getAmount()), absorbed, 0);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public void onHeal(LivingHealEvent e) {
        if (!(e.getEntityLiving() instanceof EntityPlayerMP) || !WarbandConfig.recap.enabled
                || !WarbandConfig.recap.showHealing || e.getAmount() <= 0) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) e.getEntityLiving();
        store.add(p.getUniqueID(), Hit.heal(now(), e.getAmount(), p.getHealth()), WarbandConfig.recap.maxHits);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || pending.isEmpty()) {
            return;
        }
        Iterator<UUID> it = pending.keySet().iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Pending p = pending.get(id);
            it.remove();
            resolveDelta(p, false);
        }
    }

    /** Called at death (before the snapshot) and before a new hurt replaces the pending one. */
    void resolve(UUID id, boolean death) {
        Pending p = pending.remove(id);
        if (p != null) {
            resolveDelta(p, death);
        }
    }

    void forget(UUID id) {
        pending.remove(id);
    }

    private void resolveDelta(Pending p, boolean death) {
        float hpLoss = Math.max(0, p.health - p.player.getHealth());
        float absorbed = Math.max(0, p.absorption - p.player.getAbsorptionAmount());
        int extra = 0;
        if (hpLoss <= 0 && absorbed <= 0) {
            if (!death) {
                // Nothing changed: a mod after us canceled the hit (e.g. a dodge). Not a hit.
                return;
            }
            // First Aid can kill (head/torso at 0) before vanilla health moves.
            hpLoss = Math.min(p.raw, p.health);
            extra = Hit.ESTIMATED;
        }
        record(p, hpLoss, absorbed, extra);
    }

    private void record(Pending p, float hpLoss, float absorbed, int extraFlags) {
        DamageSource src = p.src;
        Entity truth = src.getTrueSource();
        Entity direct = src.getImmediateSource();
        String name;
        String entityNbt = null;
        ItemStack weapon = null;
        if (truth != null) {
            name = truth.getName();
            entityNbt = Chat.entityNbt(truth);
            weapon = weaponOf(truth, direct);
        } else if (direct != null) {
            name = direct.getName();
            entityNbt = Chat.entityNbt(direct);
        } else {
            name = DamageNames.environment(src.getDamageType(), p.player.isPotionActive(MobEffects.POISON));
        }
        int flags = extraFlags;
        if (src.isProjectile()) {
            flags |= Hit.PROJ;
        }
        if (src.isExplosion()) {
            flags |= Hit.EXPL;
        }
        if (src.isFireDamage()) {
            flags |= Hit.FIRE;
        }
        if (src.isMagicDamage()) {
            flags |= Hit.MAGIC;
        }
        if (src.isUnblockable()) {
            flags |= Hit.BYPASS;
        }
        boolean hasWeapon = weapon != null && !weapon.isEmpty();
        Hit h = new Hit(p.tick, src.getDamageType(), name, entityNbt,
                hasWeapon ? weapon.getDisplayName() : null, hasWeapon ? Chat.itemNbt(weapon) : null,
                p.raw, hpLoss, absorbed, p.health, p.absorption, flags);
        store.add(p.player.getUniqueID(), h, WarbandConfig.recap.maxHits);
    }

    /** Player: the bow that fired the projectile, else main hand. Mob: main hand if any. */
    @Nullable
    private static ItemStack weaponOf(Entity truth, @Nullable Entity direct) {
        if (truth instanceof EntityPlayer) {
            if (direct != null && direct != truth) {
                ItemStack launcher = ProjectileOrigin.launcherOf(direct);
                if (launcher != null) {
                    return launcher;
                }
            }
            return ((EntityPlayer) truth).getHeldItemMainhand();
        }
        if (truth instanceof EntityLivingBase) {
            return ((EntityLivingBase) truth).getHeldItemMainhand();
        }
        return null;
    }
}
