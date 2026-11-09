package io.github.tidefox.dregorapatches.trick;

import io.github.tidefox.dregorapatches.util.Chat;
import io.github.tidefox.dregorapatches.util.Fmt;
import io.github.tidefox.dregorapatches.combat.ShotTag;
import io.github.tidefox.dregorapatches.PatchesConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Long-range arrow kills of announced mobs → callout + records (docs/06 §2.3–2.5). Observer only. */
public final class TrickShotListener {

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public void onDeath(LivingDeathEvent e) {
        PatchesConfig.TrickShots c = PatchesConfig.trickshots;
        EntityLivingBase victim = e.getEntityLiving();
        if (!c.enabled || victim.world.isRemote || victim instanceof EntityPlayerMP) {
            return;
        }
        Entity direct = e.getSource().getImmediateSource();
        Entity truth = e.getSource().getTrueSource();
        if (direct == null || !(truth instanceof EntityPlayerMP)) {
            return;
        }
        ShotTag shot = ShotTag.read(direct);
        EntityPlayerMP shooter = (EntityPlayerMP) truth;
        // Dispenser arrows carry no tag; a tag from another player (deflected arrow) is not this shot.
        if (shot == null || !shot.shooter.equals(shooter.getCachedUniqueIdString())) {
            return;
        }
        double dist = shot.distanceTo(victim);
        if (dist < c.minDistance) {
            return;
        }
        boolean announced = !c.requireAnnounced || AnnouncedMobs.evaluate(victim) != null;
        if (!announced) {
            if (c.privateActionBar) {
                shooter.sendStatusMessage(Chat.text(Fmt.meters(dist), TextFormatting.GOLD), true);
            }
            return;
        }
        Tiers.Tier tier = Tiers.parse(c.tiers).of(dist);
        String tierName = tier != null ? tier.name : "Trick shot";
        long flight = Math.max(0, victim.world.getTotalWorldTime() - shot.tick);

        MinecraftServer server = shooter.getServer();
        RecordBook.Result result = null;
        if (server != null) {
            ShotRecords records = ShotRecords.get(server);
            result = records.book.submit(new RecordBook.Entry(shooter.getCachedUniqueIdString(), shooter.getName(),
                    dist, victim.getName(), System.currentTimeMillis()));
            records.markDirty();
        }
        ITextComponent rich = message(shooter, victim, shot, tierName, dist, flight, result, true);
        ITextComponent msg = Chat.fit(rich, message(shooter, victim, shot, tierName, dist, flight, result, false));
        deliver(server, shooter, msg);
    }

    private static ITextComponent message(EntityPlayerMP shooter, EntityLivingBase victim, ShotTag shot,
            String tier, double dist, long flight, RecordBook.Result r, boolean hovers) {
        PatchesConfig.TrickShots c = PatchesConfig.trickshots;
        TextComponentString root = new TextComponentString("");
        root.appendSibling(Chat.text("➶ " + tier + "! ", TextFormatting.GOLD));
        root.appendSibling(Chat.text(shooter.getName(), TextFormatting.WHITE));
        root.appendSibling(Chat.text(" — " + Fmt.meters(dist) + " on ", TextFormatting.GRAY));
        String label = "[" + victim.getName() + "]";
        root.appendSibling(hovers ? Chat.entity(label, Chat.entityNbt(victim), TextFormatting.RED)
                : Chat.text(label, TextFormatting.RED));
        if (!shot.launcher.isEmpty()) {
            root.appendSibling(Chat.text(" with ", TextFormatting.GRAY));
            root.appendSibling(hovers ? Chat.item(shot.launcher, TextFormatting.AQUA)
                    : Chat.item(shot.launcher.getDisplayName(), null, TextFormatting.AQUA));
        }
        StringBuilder tags = new StringBuilder();
        if (c.showTags && shot.mounted) {
            tags.append(" · mounted on ").append(shot.mount);
        } else if (c.showTags && shot.airborne) {
            tags.append(" · airborne");
        }
        if (c.showFlightTime) {
            tags.append(" · ").append(Fmt.seconds(flight)).append(" flight");
        }
        if (tags.length() > 0) {
            root.appendSibling(Chat.text(tags.toString(), TextFormatting.GRAY));
        }
        String line2 = recordLine(r);
        if (line2 != null) {
            root.appendSibling(new TextComponentString("\n   "));
            root.appendSibling(Chat.text("★ " + line2, TextFormatting.YELLOW));
        }
        return root;
    }

    /** Server record beats personal best; nothing for an ordinary shot. */
    static String recordLine(RecordBook.Result r) {
        if (r == null) {
            return null;
        }
        if (r.serverRecord && r.previousRecord != null) {
            return "new server record (prev " + Fmt.meters(r.previousRecord.distance) + " by "
                    + r.previousRecord.player + ")";
        }
        if (r.personalBest && r.previousBest != null) {
            return "new personal best (prev " + Fmt.meters(r.previousBest.distance) + ")";
        }
        if (r.personalBest) {
            return "first trick shot";
        }
        return null;
    }

    private static void deliver(MinecraftServer server, EntityPlayerMP shooter, ITextComponent msg) {
        PatchesConfig.TrickShots c = PatchesConfig.trickshots;
        if (server == null || c.scope == PatchesConfig.Scope.GLOBAL) {
            if (server != null) {
                Chat.broadcast(server, msg);
            } else {
                Chat.send(shooter, msg);
            }
            return;
        }
        double r2 = (double) c.radius * c.radius;
        for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
            if (p.dimension != shooter.dimension) {
                continue;
            }
            if (c.scope == PatchesConfig.Scope.RADIUS && p.getDistanceSq(shooter) > r2) {
                continue;
            }
            Chat.send(p, msg);
        }
    }
}
