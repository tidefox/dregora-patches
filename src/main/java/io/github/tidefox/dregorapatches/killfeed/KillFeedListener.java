package io.github.tidefox.dregorapatches.killfeed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import io.github.tidefox.dregorapatches.DregoraPatches;
import io.github.tidefox.dregorapatches.PatchesConfig;
import io.github.tidefox.dregorapatches.combat.DebugProbe;
import io.github.tidefox.dregorapatches.combat.ShotTag;
import io.github.tidefox.dregorapatches.trick.AnnouncedMobs;
import io.github.tidefox.dregorapatches.trick.TrickShotListener;
import io.github.tidefox.dregorapatches.util.Chat;
import io.github.tidefox.dregorapatches.util.Fmt;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * One-line kill feed (docs/03 §2.6). A player-credited death becomes a pending kill at HIGHEST, before
 * any mod announces it. {@link ChatTap} hands every system line to {@link #offer}; the kill line of a
 * pending kill (vanilla, Scaling Health, Champions, …) is held back and, at tick end, replaced by
 * vanilla's death message with mod badges, the weapon icon and one extra:
 * <pre>
 * Blight Defiled Slime was slain by JCRVIII using [icon]
 * Stage 4 Fire Dragon was shot by Bob using [icon] · 112m
 * </pre>
 * Notable kills nothing announced get the same line. Repeated kills of one mob type by one player
 * collapse into a summary. If building our line fails, the held lines are sent unchanged.
 */
public final class KillFeedListener {

    /** Fight state plus the weapon of the last direct hit (for kills by fire/bleed/fall afterwards). */
    private static final class Tracked {
        final Fight fight = new Fight();
        ItemStack weapon = ItemStack.EMPTY;
    }

    /** A line we took; {@code receivers == null}: it was a broadcast. */
    private static final class Held {
        final ITextComponent line;
        final String text;
        @Nullable
        final Set<EntityPlayerMP> receivers;

        Held(ITextComponent line, String text, @Nullable Set<EntityPlayerMP> receivers) {
            this.line = line;
            this.text = text;
            this.receivers = receivers;
        }
    }

    private static final class Pending {
        final EntityLivingBase victim;
        final long deadline;
        final Set<String> names, credits;
        final boolean blight, notable, whitelisted;
        @Nullable
        final ITextComponent death;
        final KillCredit credit;
        final ItemStack weapon;
        final double distance;
        final float finalHit;
        final long fightTicks;
        final List<Held> held = new ArrayList<>();
        @Nullable
        Badges badges;
        @Nullable
        TrickShotListener.Callout trick;
        boolean trickRead;

        Pending(EntityLivingBase victim, long deadline, boolean notable, boolean whitelisted,
                @Nullable ITextComponent death, KillCredit credit, ItemStack weapon, double distance,
                float finalHit, long fightTicks) {
            this.victim = victim;
            this.deadline = deadline;
            this.names = names(victim);
            this.blight = victim.getEntityData().getBoolean(AnnouncedMobs.BLIGHT_KEY);
            this.notable = notable;
            this.whitelisted = whitelisted;
            this.death = death;
            this.credit = credit;
            Set<String> k = new LinkedHashSet<>();
            if (credit.kind != KillCredit.Kind.LINGERING) { // "X burned to death" may not name the player
                k.add(credit.player.getName());
                if (credit.pet != null) {
                    k.add(plain(credit.pet.getDisplayName()));
                }
            }
            this.credits = k;
            this.weapon = weapon;
            this.distance = distance;
            this.finalHit = finalHit;
            this.fightTicks = fightTicks;
        }

        @Nullable
        Badges badges() {
            if (badges == null && !(victim instanceof EntityPlayer)) {
                badges = Badges.of(victim);
            }
            return badges;
        }

        @Nullable
        TrickShotListener.Callout trick() {
            if (!trickRead) {
                trick = TrickShotListener.take(victim);
                trickRead = true;
            }
            return trick;
        }

        void hold(ITextComponent c, String text, @Nullable EntityPlayerMP receiver) {
            if (receiver != null) {
                for (Held h : held) {
                    if (h.receivers != null && h.text.equals(text) && h.receivers.add(receiver)) {
                        return; // the same line sent player by player
                    }
                }
            }
            held.add(new Held(c, text, receiver == null ? null : new HashSet<>(Collections.singleton(receiver))));
        }

        /** Who gets our line: everyone if any held line was a broadcast (or none was held). */
        @Nullable
        Set<EntityPlayerMP> audience() {
            Set<EntityPlayerMP> out = new HashSet<>();
            for (Held h : held) {
                if (h.receivers == null) {
                    return null;
                }
                out.addAll(h.receivers);
            }
            return held.isEmpty() ? null : out;
        }
    }

    /** One player killing one mob type again and again. */
    private static final class Streak {
        final long first;
        long last;
        int more, blights;
        final ITextComponent mob;
        @Nullable
        final Set<EntityPlayerMP> audience;

        Streak(long tick, ITextComponent mob, @Nullable Set<EntityPlayerMP> audience) {
            this.first = this.last = tick;
            this.mob = mob;
            this.audience = audience;
        }
    }

    private final Map<EntityLivingBase, Tracked> fights = new WeakHashMap<>();
    /** Mods that fire the death event twice (corpse mobs) get one line. */
    private final Map<EntityLivingBase, Boolean> handled = new WeakHashMap<>();
    private final List<Pending> pending = new ArrayList<>();
    private final Map<String, Streak> streaks = new HashMap<>();

    public KillFeedListener() {
        ChatTap.sink = this;
    }

    /** Trick shots go into our line instead of their own callout (docs/06). */
    public static boolean carriesTrickShots() {
        return PatchesConfig.killfeed.enabled && ChatTap.sink != null && ChatTap.live();
    }

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

    /** HIGHEST: the kill must be pending before Scaling Health / Champions send their line. Reads only. */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = false)
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
        if (!ChatTap.live()) {
            note(victim, "chat hooks missing → kill feed off");
            return;
        }
        if (isPlayer && (!c.playerKills || !victim.world.getGameRules().getBoolean("showDeathMessages"))) {
            note(victim, "player kill, but playerKills off or showDeathMessages false");
            return;
        }
        boolean notable = !isPlayer && (reason != null && (reason != AnnouncedMobs.Reason.NAMED || c.announceNamed)
                || c.announceMinMaxHealth > 0 && victim.getMaxHealth() >= c.announceMinMaxHealth);

        ItemStack weapon = ItemStack.EMPTY;
        double distance = Double.NaN;
        if (credit.kind == KillCredit.Kind.DIRECT) {
            ShotTag shot = ownShot(credit.player, e.getSource());
            weapon = (shot != null ? shot.launcher : credit.player.getHeldItemMainhand()).copy();
            if (shot != null) {
                distance = shot.distanceTo(victim);
            }
        } else if (credit.kind == KillCredit.Kind.LINGERING && tracked != null) {
            weapon = tracked.weapon;
        }
        ITextComponent death;
        try {
            death = victim.getCombatTracker().getDeathMessage(); // vanilla's wording: slain/shot/blown up by …
        } catch (RuntimeException ex) {
            death = null;
        }
        pending.add(new Pending(victim, server.getTickCounter() + c.lineWaitTicks, notable,
                reason == AnnouncedMobs.Reason.WHITELIST, death, credit, weapon, distance,
                fight == null ? Float.NaN : fight.finalHit(now), fight == null ? -1 : fight.duration(now, timeoutTicks())));
    }

    /** A death another mod canceled (totem, revive): the kill never happened, give its lines back. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onDeathCanceled(LivingDeathEvent e) {
        if (!e.isCanceled()) {
            return;
        }
        handled.remove(e.getEntityLiving());
        MinecraftServer server = e.getEntityLiving().world.getMinecraftServer();
        for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (p.victim == e.getEntityLiving()) {
                if (server != null) {
                    giveBack(server, p);
                }
                it.remove();
            }
        }
    }

    /** From {@link ChatTap}: true = this line is the kill line of a pending kill and is held back. */
    boolean offer(ITextComponent c, String text, @Nullable EntityPlayerMP receiver) {
        // Newest first: mods announce a kill inside its own death event.
        for (int i = pending.size() - 1; i >= 0; i--) {
            Pending p = pending.get(i);
            if (KillLine.matches(text, p.names, p.blight, p.credits)) {
                p.hold(c, text, receiver);
                return true;
            }
        }
        return false;
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

    // ---- tick end ----------------------------------------------------------------------

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
        int tick = server.getTickCounter();
        endStreaks(server, tick);
        for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (!p.held.isEmpty()) {
                finish(server, p, tick);
                it.remove();
            } else if (tick >= p.deadline) {
                if (PatchesConfig.killfeed.announceUnannounced && isNotable(p)) {
                    finish(server, p, tick);
                } else {
                    note(p, "no kill line in chat, not notable → nothing");
                }
                it.remove();
            }
        }
    }

    private static boolean isNotable(Pending p) {
        if (p.notable || p.trick() != null) {
            return true;
        }
        Badges b = p.badges(); // null for players: a PvP kill without a vanilla line stays silent
        return b != null && b.elite;
    }

    /** Our line (or a streak count) for a kill; on any failure the held lines go out unchanged. */
    private void finish(MinecraftServer server, Pending p, int tick) {
        try {
            Badges b = p.badges();
            TrickShotListener.Callout trick = p.trick();
            boolean single = b == null || b.boss || b.elite || p.whitelisted || p.victim.hasCustomName() || trick != null;
            String key = single ? null : p.credit.player.getCachedUniqueIdString() + "|" + EntityList.getKey(p.victim);
            Streak s = key == null ? null : streaks.get(key);
            if (s != null) {
                s.more++;
                s.blights += b.blight ? 1 : 0;
                s.last = tick;
                note(p, "streak, counted (" + s.more + " more)");
                return;
            }
            ITextComponent line = line(p, b, trick);
            if (Chat.jsonLength(line) >= Chat.MAX_JSON) {
                throw new IllegalStateException("kill line too large");
            }
            Set<EntityPlayerMP> to = p.audience();
            send(server, line, to);
            if (trick != null && trick.record != null) {
                send(server, Chat.text("  ★ " + trick.record, TextFormatting.YELLOW), to);
            }
            if (key != null && PatchesConfig.killfeed.streakSec > 0) {
                streaks.put(key, new Streak(tick, mobName(p.victim), to));
            }
            note(p, p.held.isEmpty() ? "own line (notable, nothing announced it)" : "replaced: " + p.held.get(0).text);
        } catch (RuntimeException ex) {
            DregoraPatches.LOGGER.warn("Kill feed line failed, sending the original kill line", ex);
            giveBack(server, p);
        }
    }

    private static void giveBack(MinecraftServer server, Pending p) {
        for (Held h : p.held) {
            send(server, h.line, h.receivers);
        }
    }

    private static void send(MinecraftServer server, ITextComponent c, @Nullable Set<EntityPlayerMP> to) {
        if (to == null) {
            Chat.broadcast(server, c);
            return;
        }
        for (EntityPlayerMP pl : to) {
            if (!pl.hasDisconnected()) {
                Chat.send(pl, c);
            }
        }
    }

    /** Streaks with no kill for {@code streakSec} → one summary line. */
    private void endStreaks(MinecraftServer server, int tick) {
        long window = PatchesConfig.killfeed.streakSec * 20L;
        for (Iterator<Streak> it = streaks.values().iterator(); it.hasNext(); ) {
            Streak s = it.next();
            if (tick - s.last <= window) {
                continue;
            }
            it.remove();
            if (s.more > 0) {
                TextComponentString line = Chat.text("  …and " + s.more + " more ", TextFormatting.GRAY);
                line.appendSibling(s.mob);
                line.appendSibling(Chat.text(" kills" + KillLine.streakTail(s.blights, s.last - s.first), TextFormatting.GRAY));
                send(server, line, s.audience);
            }
        }
    }

    private static ITextComponent mobName(EntityLivingBase e) {
        ResourceLocation id = EntityList.getKey(e);
        String t = id == null ? null : EntityList.getTranslationName(id);
        ITextComponent c = t == null ? new TextComponentString(e.getName()) : new TextComponentTranslation("entity." + t + ".name");
        c.getStyle().setColor(TextFormatting.GRAY);
        return c;
    }

    // ---- the line ----------------------------------------------------------------------

    /** Vanilla's death message, victim replaced by badges + name, weapon icon, one extra. */
    static ITextComponent line(Pending p, @Nullable Badges b, @Nullable TrickShotListener.Callout trick) {
        ITextComponent death = p.death != null ? p.death
                : new TextComponentTranslation("death.attack.player", p.victim.getDisplayName(), p.credit.player.getDisplayName());
        boolean weaponShown = false;
        if (death instanceof TextComponentTranslation) {
            TextComponentTranslation t = (TextComponentTranslation) death;
            Object[] args = t.getFormatArgs().clone();
            if (args.length > 0 && b != null) {
                args[0] = victimName(p, b);
            }
            if (p.credit.kind == KillCredit.Kind.PET && p.credit.pet != null && args.length > 1) {
                args[1] = new TextComponentString(p.credit.player.getName() + "'s " + plain(p.credit.pet.getDisplayName()));
            }
            if (t.getKey().endsWith(".item") && args.length > 2 && !p.weapon.isEmpty()) {
                args[2] = Chat.icon(p.weapon); // vanilla names the item in hand; ours is the launcher
                weaponShown = true;
            }
            TextComponentTranslation n = new TextComponentTranslation(t.getKey(), args);
            n.setStyle(t.getStyle().createShallowCopy());
            death = n;
        }
        TextComponentString root = new TextComponentString("");
        root.appendSibling(death);
        if (!weaponShown && !p.weapon.isEmpty()) {
            root.appendSibling(new TextComponentString(" using"));
            root.appendSibling(Chat.icon(p.weapon));
        }
        if (p.credit.kind == KillCredit.Kind.LINGERING
                && !plain(death).toLowerCase(Locale.ROOT).contains(p.credit.player.getName().toLowerCase(Locale.ROOT))) {
            root.appendSibling(Chat.text(" after " + p.credit.player.getName() + "'s hit", TextFormatting.GRAY));
        }
        boolean far = !Double.isNaN(p.distance) && p.distance >= PatchesConfig.trickshots.minDistance;
        if (trick != null || far) {
            root.appendSibling(Chat.hoverText(" · " + Fmt.meters(p.distance), trick != null ? trick.detail : fightInfo(p),
                    TextFormatting.AQUA));
        } else if (p.fightTicks >= PatchesConfig.killfeed.minFightSec * 20L && p.fightTicks > 0) {
            root.appendSibling(Chat.hoverText(" · " + Fmt.duration(p.fightTicks), fightInfo(p), TextFormatting.GRAY));
        }
        return root;
    }

    private static ITextComponent victimName(Pending p, Badges b) {
        StringBuilder hover = new StringBuilder(plain(p.victim.getDisplayName()));
        for (String d : b.details) {
            hover.append('\n').append(d);
        }
        String fight = fightInfo(p);
        if (!fight.isEmpty()) {
            hover.append('\n').append(fight);
        }
        TextComponentString c = Chat.text(b.prefix + plain(p.victim.getDisplayName()), b.color);
        c.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(hover.toString())));
        return c;
    }

    /** {@code final 33.7 · fight 1m12s}, parts unknown left out. */
    private static String fightInfo(Pending p) {
        StringBuilder sb = new StringBuilder();
        if (!Float.isNaN(p.finalHit)) {
            sb.append("final hit ").append(Fmt.num(p.finalHit));
        }
        if (p.fightTicks >= 20) {
            sb.append(sb.length() > 0 ? " · " : "").append("fight ").append(Fmt.duration(p.fightTicks));
        }
        return sb.toString();
    }

    // ---- debug -------------------------------------------------------------------------

    private static void note(EntityLivingBase victim, String what) {
        if (DebugProbe.INSTANCE.killFeedArmed()) {
            DebugProbe.INSTANCE.killFeed(plain(victim.getDisplayName()), what);
        }
    }

    private static void note(Pending p, String what) {
        if (DebugProbe.INSTANCE.killFeedArmed()) {
            DebugProbe.INSTANCE.killFeed(plain(p.victim.getDisplayName()) + " (by " + p.credit.player.getName() + ", "
                    + p.credit.kind + ")", what);
        }
    }

    private static String describe(@Nullable Entity e) {
        return e == null ? "-" : e.getName();
    }

    /** Server stopped: nobody left to send to. */
    public void clear() {
        fights.clear();
        handled.clear();
        pending.clear();
        streaks.clear();
    }
}
