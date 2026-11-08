package io.github.tidefox.dregorapatches.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/**
 * Tags every player-fired {@link EntityArrow} with {@link ShotTag} (docs/06 §2.1), shared by
 * 03/06. {@code ArrowLooseEvent} fires before the arrow spawns and names the bow; the stash
 * is kept for the whole tick so every Multishot/Splitshot arrow of one release gets the bow.
 * Observer only: nothing is canceled or changed except our own key in the arrow's Forge data.
 */
public final class ProjectileOrigin {

    private static final class Pending {
        final long tick;
        final ItemStack bow;

        Pending(long tick, ItemStack bow) {
            this.tick = tick;
            this.bow = bow;
        }
    }

    private final Map<UUID, Pending> pending = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public void onArrowLoose(ArrowLooseEvent e) {
        EntityPlayer p = e.getEntityPlayer();
        if (p.world.isRemote || e.getBow().isEmpty()) {
            return;
        }
        pending.put(p.getUniqueID(), new Pending(p.world.getTotalWorldTime(), e.getBow().copy()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public void onJoin(EntityJoinWorldEvent e) {
        Entity ent = e.getEntity();
        if (e.getWorld().isRemote || !(ent instanceof EntityArrow)) {
            return;
        }
        Entity shooter = ((EntityArrow) ent).shootingEntity;
        // Re-joins (chunk reload, dimension change) already carry the tag: keep the original.
        if (!(shooter instanceof EntityPlayerMP) || ShotTag.has(ent)) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) shooter;
        long now = p.world.getTotalWorldTime();
        Entity mount = p.getRidingEntity();
        new ShotTag(p.posX, p.posY, p.posZ, now, p.getCachedUniqueIdString(), launcherFor(p, now),
                !p.onGround && mount == null, mount != null, mount != null ? mount.getName() : "").write(ent);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        pending.remove(e.player.getUniqueID());
    }

    /** Stash from this tick, else the item being drawn, else a held bow-like item, else main hand. */
    private ItemStack launcherFor(EntityPlayerMP p, long now) {
        Pending s = pending.get(p.getUniqueID());
        if (s != null && s.tick == now) {
            return s.bow;
        }
        ItemStack active = p.getActiveItemStack();
        if (!active.isEmpty()) {
            return active.copy();
        }
        for (EnumHand hand : EnumHand.values()) {
            ItemStack held = p.getHeldItem(hand);
            if (looksLikeLauncher(held)) {
                return held.copy();
            }
        }
        return p.getHeldItemMainhand().copy();
    }

    private static boolean looksLikeLauncher(ItemStack s) {
        if (s.isEmpty()) {
            return false;
        }
        ResourceLocation id = s.getItem().getRegistryName();
        String path = id == null ? "" : id.getPath();
        String cls = s.getItem().getClass().getSimpleName().toLowerCase();
        return path.contains("bow") || cls.contains("bow");
    }

    /** Launcher of a tagged projectile, or null. */
    @Nullable
    public static ItemStack launcherOf(@Nullable Entity projectile) {
        ShotTag t = ShotTag.read(projectile);
        return t == null || t.launcher.isEmpty() ? null : t.launcher;
    }
}
