package io.github.tidefox.dregorapatches.killfeed;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import io.github.tidefox.dregorapatches.PatchesConfig;
import io.github.tidefox.dregorapatches.combat.DebugProbe;
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
 * Kill feed (docs/03): one follow-up line under the kill line that is already in chat:
 * <pre>
 * Blight Xaphan Lvl 4 was killed by Alice!     ← the pack's / vanilla's own line, untouched
 *   ↳ with [Longbow] · 97m · final 22.0 · 1m12s
 * </pre>
 * Dynamic (§2.5): {@link ChatTap} watches the server's system messages; any line naming the dead
 * mob or player within {@code lineWaitTicks} gets the follow-up, whichever mod sent it. A notable
 * mob (dragon, boss, …) that nothing announced gets our own kill line plus the follow-up.
 * Observer only: listens at LOWEST/LOW, cancels nothing.
 */
public final class KillFeedListener {

    /** Fight state plus the weapon of the last direct hit (for kills by fire/bleed/fall afterwards). */
    private static final class Tracked {
        final Fight fight = new Fight();
        ItemStack weapon = ItemStack.EMPTY;
    }

    /** A kill waiting for its line in chat. Components are built only when something is sent. */
    private static final class Pending {
        final long sinceTick, deadline;
        final Set<String> names;
        final boolean notable, blight;
        final String victimName;
        @Nullable
        final String victimNbt;
        final String killerName;
        final KillCredit.Kind kind;
        @Nullable
        final String petName, petNbt;
        final ItemStack weapon;
        final String tail;

        Pending(long sinceTick, long deadline, Set<String> names, boolean notable, boolean blight, String victimName,
                @Nullable String victimNbt, String killerName, KillCredit.Kind kind, @Nullable Entity pet,
                ItemStack weapon, String tail) {
            this.sinceTick = sinceTick;
            this.deadline = deadline;
            this.names = names;
            this.notable = notable;
            this.blight = blight;
            this.victimName = victimName;
            this.victimNbt = victimNbt;
            this.killerName = killerName;
            this.kind = kind;
            this.petName = pet == null ? null : plain(pet.getDisplayName());
            this.petNbt = pet == null ? null : Chat.entityNbt(pet);
            this.weapon = weapon;
            this.tail = tail;
        }

        ITextComponent follow() {
            return Chat.fit(followLine(this, true), followLine(this, false));
        }

        ITextComponent own() {
            return Chat.fit(ownLine(this, true), ownLine(this, false));
        }
    }

    private final Map<EntityLivingBase, Tracked> fights = new WeakHashMap<>();
    /** Mods that fire the death event twice (corpse mobs) get one line. */
    private final Map<EntityLivingBase, Boolean> handled = new WeakHashMap<>();
    private final List<Pending> pending = new ArrayList<>();
    /** Fallback path (no chat tap): follow-ups sent at tick end as before. */
    private final List<ITextComponent> queue = new ArrayList<>();

    // ---- fight tracking ----------------------------------------------------------------

    /** Mobs: post-armor amount. Doesn't fire for players (First Aid cancels their hurt at LOWEST). */
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
        if (!c.enabled || victim.world.isRemote) {
            return;
        }
        // Cheap checks first: this runs for every hit on every entity.
        EntityPlayerMP attacker = KillCredit.killer(victim, src);
        ItemStack weapon = null;
        if (attacker != null) {
            weapon = weaponOf(attacker, src);
        } else if (c.petKills && src.getTrueSource() != null) {
            MinecraftServer server = victim.world.getMinecraftServer();
            attacker = server == null ? null : KillCredit.owner(victim, src, server);
        }
        if (attacker == null) {
            return;
        }
        Tracked t = fights.get(victim);
        if (t == null) {
            t = new Tracked();
            fights.put(victim, t);
        }
        t.fight.hit(victim.world.getTotalWorldTime(), amount, timeoutTicks(), attacker.getCachedUniqueIdString());
        if (weapon != null) {
            t.weapon = weapon.isEmpty() ? ItemStack.EMPTY : weapon.copy();
        }
    }

    /** Tagged shot from this player → the launcher that fired it; else the main hand. */
    private static ItemStack weaponOf(EntityPlayerMP player, DamageSource src) {
        ShotTag shot = ownShot(player, src);
        return shot != null ? shot.launcher : player.getHeldItemMainhand();
    }

    @Nullable
    private static ShotTag ownShot(EntityPlayerMP player, DamageSource src) {
        ShotTag shot = ShotTag.read(src.getImmediateSource());
        return shot != null && shot.shooter.equals(player.getCachedUniqueIdString()) ? shot : null; // deflected: not theirs
    }

    private static long timeoutTicks() {
        return PatchesConfig.killfeed.fightTimeoutSec * 20L;
    }

    // ---- death -------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public void onDeath(LivingDeathEvent e) {
        EntityLivingBase victim = e.getEntityLiving();
        Tracked tracked = fights.remove(victim);
        PatchesConfig.KillFeed c = PatchesConfig.killfeed;
        MinecraftServer server = victim.world.getMinecraftServer();
        if (!c.enabled || victim.world.isRemote || server == null) {
            return;
        }
        if (handled.put(victim, Boolean.TRUE) != null) {
            return;
        }
        boolean isPlayer = victim instanceof EntityPlayer;
        AnnouncedMobs.Reason reason = isPlayer ? null : AnnouncedMobs.cached(victim);
        AnnouncedMobs.forget(victim);

        long now = victim.world.getTotalWorldTime();
        Fight fight = tracked == null ? null : tracked.fight;
        KillCredit credit = KillCredit.of(victim, e.getSource(), fight, now, c.petKills, c.lingerCreditSec * 20L, server);
        if (credit == null) {
            if (reason != null) {
                note(victim, "no player credit (true source " + describe(e.getSource().getTrueSource()) + ")");
            }
            return;
        }
        if (isPlayer && (!c.playerKills || !victim.world.getGameRules().getBoolean("showDeathMessages"))) {
            note(victim, "player kill, but playerKills off or showDeathMessages false");
            return;
        }
        boolean notable = !isPlayer && (reason != null && (reason != AnnouncedMobs.Reason.NAMED || c.announceNamed)
                || c.announceMinMaxHealth > 0 && victim.getMaxHealth() >= c.announceMinMaxHealth);

        // Weapon, distance, final hit, fight time.
        ItemStack weapon = ItemStack.EMPTY;
        double distance = Double.NaN;
        if (credit.kind == KillCredit.Kind.DIRECT) {
            ShotTag shot = ownShot(credit.player, e.getSource());
            weapon = (shot != null ? shot.launcher : credit.player.getHeldItemMainhand()).copy();
            if (shot != null && c.showDistance) {
                distance = shot.distanceTo(victim);
            }
        } else if (credit.kind == KillCredit.Kind.LINGERING && tracked != null) {
            weapon = tracked.weapon;
        }
        double finalHit = fight != null && c.showFinalHit ? fight.finalHit(now) : Double.NaN;
        long duration = fight != null && c.showDuration ? fight.duration(now, timeoutTicks()) : -1;
        String tail = KillLine.tail(distance, finalHit, duration);

        int tick = server.getTickCounter();
        Pending p = new Pending(tick, tick + c.lineWaitTicks, names(victim), notable,
                victim.getEntityData().getBoolean(AnnouncedMobs.BLIGHT_KEY), plain(victim.getDisplayName()),
                Chat.entityNbt(victim), credit.player.getName(), credit.kind, credit.pet, weapon, tail);

        if (ChatTap.live()) {
            pending.add(p);
            return;
        }
        // No chat tap: the old static rules (docs/03 §2.1).
        if (isPlayer || c.announcedMobKills && reason != null) {
            queue.add(p.follow());
            note(victim, "follow-up (static rules; chat tap off)");
        } else {
            note(victim, "nothing (static rules; chat tap off, not an announced mob)");
        }
    }

    /** Names a kill line could use for this entity, formatting stripped. */
    private static Set<String> names(EntityLivingBase e) {
        Set<String> out = new LinkedHashSet<>();
        out.add(plain(e.getDisplayName()));
        out.add(KillLine.strip(e.getName()));
        if (e.hasCustomName()) {
            out.add(KillLine.strip(e.getCustomNameTag()));
        }
        out.remove("");
        return out;
    }

    private static String plain(ITextComponent c) {
        try {
            return KillLine.strip(c.getUnformattedText());
        } catch (RuntimeException ex) {
            return "";
        }
    }

    // ---- lines -------------------------------------------------------------------------

    /** {@code ↳ with [weapon]…}, {@code ↳ Alice's [Wolf]…} or {@code ↳ Alice's last hit, with [weapon]…}. */
    static ITextComponent followLine(Pending p, boolean hovers) {
        TextComponentString root = new TextComponentString("");
        root.appendSibling(Chat.text("  ↳ ", TextFormatting.GRAY));
        if (p.kind == KillCredit.Kind.PET) {
            root.appendSibling(Chat.text(p.killerName + "'s ", TextFormatting.GRAY));
            root.appendSibling(Chat.entity("[" + p.petName + "]", hovers ? p.petNbt : null, TextFormatting.AQUA));
        } else {
            if (p.kind == KillCredit.Kind.LINGERING) {
                root.appendSibling(Chat.text(p.killerName + "'s last hit, ", TextFormatting.GRAY));
            }
            if (p.weapon.isEmpty()) {
                root.appendSibling(Chat.text("bare-handed", TextFormatting.GRAY));
            } else {
                root.appendSibling(Chat.text("with ", TextFormatting.GRAY));
                root.appendSibling(hovers ? Chat.item(p.weapon, TextFormatting.AQUA)
                        : Chat.item(p.weapon.getDisplayName(), null, TextFormatting.AQUA));
            }
        }
        if (!p.tail.isEmpty()) {
            root.appendSibling(Chat.text(p.tail, TextFormatting.GRAY));
        }
        return root;
    }

    /** Our own kill line for a notable mob nothing else announced: {@code [Fire Dragon] was slain by Alice}. */
    static ITextComponent ownLine(Pending p, boolean hovers) {
        TextComponentString root = new TextComponentString("");
        root.appendSibling(Chat.entity(p.victimName, hovers ? p.victimNbt : null, TextFormatting.GOLD));
        root.appendSibling(Chat.text(KillLine.verb(p.kind, p.killerName, p.petName), TextFormatting.WHITE));
        return root;
    }

    // ---- tick end: match lines, send -----------------------------------------------------

    /** After every listener and vanilla's death message have run. */
    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }
        if (!queue.isEmpty()) {
            for (ITextComponent msg : queue) {
                Chat.broadcast(server, msg);
            }
            queue.clear();
        }
        int tick = server.getTickCounter();
        if (!pending.isEmpty()) {
            // Notable kills pick first: a plain mob dying in the same tick as a same-named Blight
            // must not take the Blight's line (docs/03 §2.6, split slimes). Stable sort keeps age order.
            pending.sort((a, b) -> Boolean.compare(b.notable, a.notable));
            for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
                Pending p = it.next();
                String line = ChatTap.LINES.claim(p.sinceTick, p.names, p.blight);
                if (line != null) {
                    Chat.broadcast(server, p.follow());
                    note(p, "follow-up under: " + line);
                    it.remove();
                } else if (tick >= p.deadline) {
                    if (p.notable && PatchesConfig.killfeed.announceUnannounced) {
                        Chat.broadcast(server, p.own());
                        Chat.broadcast(server, p.follow());
                        note(p, "no kill line in chat, notable → own line + follow-up");
                    } else {
                        note(p, "no kill line in chat, not notable → nothing");
                    }
                    it.remove();
                }
            }
        }
        ChatTap.LINES.prune(tick - PatchesConfig.killfeed.lineWaitTicks - 2);
    }

    // ---- debug -------------------------------------------------------------------------

    private static void note(EntityLivingBase victim, String what) {
        if (!DebugProbe.INSTANCE.killFeedArmed()) {
            return;
        }
        DebugProbe.INSTANCE.killFeed(plain(victim.getDisplayName()), what);
    }

    private static void note(Pending p, String what) {
        if (!DebugProbe.INSTANCE.killFeedArmed()) {
            return;
        }
        DebugProbe.INSTANCE.killFeed(p.victimName + " (by " + p.killerName + ", " + p.kind + ")", what);
    }

    private static String describe(@Nullable Entity e) {
        return e == null ? "-" : e.getName();
    }

    public void clear() {
        fights.clear();
        handled.clear();
        pending.clear();
        queue.clear();
        ChatTap.LINES.clear();
    }
}
