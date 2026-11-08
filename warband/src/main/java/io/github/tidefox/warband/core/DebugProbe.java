package io.github.tidefox.warband.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import io.github.tidefox.warband.Warband;
import io.github.tidefox.warband.trick.AnnouncedMobs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * {@code /wb debug damage [n]} and {@code /wb debug kill [n]} (docs/00 core, 05 §1, 06 §2.6).
 * Read-only: logs what the damage pipeline and dead mobs look like so the ⚠ verify items and
 * {@code trickshots.mobs / nbtMarkers} can be filled from the real pack. Output goes to the log
 * and to the op who armed it. Disarmed, every handler returns on one int check.
 */
public final class DebugProbe {

    public static final DebugProbe INSTANCE = new DebugProbe();

    private int damageLeft, killLeft;
    @Nullable
    private UUID damageTo, killTo;

    /** One traced hurt, from the first listener that sees it to the end of the tick. */
    private static final class Trace {
        final StringBuilder sb = new StringBuilder();
        final EntityLivingBase victim;
        final DamageSource src;
        final float health, absorption;
        boolean damageSeen;

        Trace(EntityLivingBase victim, DamageSource src) {
            this.victim = victim;
            this.src = src;
            this.health = victim.getHealth();
            this.absorption = victim.getAbsorptionAmount();
        }
    }

    private final Map<EntityLivingBase, Trace> traces = new HashMap<>();
    private final List<Trace> done = new ArrayList<>();

    private DebugProbe() {}

    public void armDamage(int n, @Nullable UUID to) {
        damageLeft = n;
        damageTo = to;
        traces.clear();
    }

    public void armKill(int n, @Nullable UUID to) {
        killLeft = n;
        killTo = to;
    }

    public void off() {
        damageLeft = 0;
        killLeft = 0;
        traces.clear();
    }

    // ---- damage ------------------------------------------------------------------------

    private static boolean involvesPlayer(EntityLivingBase victim, DamageSource src) {
        return victim instanceof EntityPlayer || src.getTrueSource() instanceof EntityPlayer;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void hurtFirst(LivingHurtEvent e) {
        if (damageLeft <= 0 || e.getEntityLiving().world.isRemote
                || !involvesPlayer(e.getEntityLiving(), e.getSource()) || traces.containsKey(e.getEntityLiving())) {
            return;
        }
        damageLeft--;
        Trace t = new Trace(e.getEntityLiving(), e.getSource());
        traces.put(e.getEntityLiving(), t);
        t.sb.append("[wb debug damage] victim=").append(describe(e.getEntityLiving()))
                .append(" hp=").append(Fmt.num(t.health)).append(" abs=").append(Fmt.num(t.absorption))
                .append("\n  source: ").append(describe(e.getSource()))
                .append("\n  hurt@HIGHEST=").append(Fmt.num(e.getAmount())).append(e.isCanceled() ? " (canceled)" : "");
    }

    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
    public void hurtLow(LivingHurtEvent e) {
        Trace t = trace(e.getEntityLiving(), e.getSource());
        if (t != null) {
            t.sb.append(" @LOW=").append(Fmt.num(e.getAmount())).append(e.isCanceled() ? " (canceled)" : "");
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void hurtLowest(LivingHurtEvent e) {
        Trace t = trace(e.getEntityLiving(), e.getSource());
        if (t != null) {
            t.sb.append(" @LOWEST=").append(Fmt.num(e.getAmount())).append(e.isCanceled() ? " (canceled)" : "");
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void damage(LivingDamageEvent e) {
        Trace t = trace(e.getEntityLiving(), e.getSource());
        if (t != null) {
            t.damageSeen = true;
            t.sb.append("\n  damage@LOWEST=").append(Fmt.num(e.getAmount())).append(e.isCanceled() ? " (canceled)" : "");
        }
    }

    @Nullable
    private Trace trace(EntityLivingBase victim, DamageSource src) {
        if (traces.isEmpty()) {
            return null;
        }
        Trace t = traces.get(victim);
        return t != null && t.src == src ? t : null;
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || traces.isEmpty()) {
            return;
        }
        done.addAll(traces.values());
        traces.clear();
        for (Trace t : done) {
            if (!t.damageSeen) {
                t.sb.append("\n  damage event: not fired");
            }
            t.sb.append("\n  after tick: hp=").append(Fmt.num(t.victim.getHealth()))
                    .append(" (Δ").append(Fmt.num(t.health - t.victim.getHealth())).append(") abs=")
                    .append(Fmt.num(t.victim.getAbsorptionAmount())).append(t.victim.isDead ? " dead" : "");
            report(t.sb.toString(), damageTo);
        }
        done.clear();
    }

    // ---- kill --------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void death(LivingDeathEvent e) {
        EntityLivingBase v = e.getEntityLiving();
        if (killLeft <= 0 || v.world.isRemote || !(e.getSource().getTrueSource() instanceof EntityPlayer)) {
            return;
        }
        killLeft--;
        StringBuilder sb = new StringBuilder("[wb debug kill] ").append(describe(v));
        sb.append("\n  customName=").append(v.hasCustomName() ? v.getCustomNameTag() : "-")
                .append(" isNonBoss=").append(v.isNonBoss())
                .append(" announced=").append(AnnouncedMobs.evaluate(v))
                .append(e.isCanceled() ? " (death canceled)" : "");
        sb.append("\n  source: ").append(describe(e.getSource()));
        ShotTag shot = ShotTag.read(e.getSource().getImmediateSource());
        if (shot != null) {
            sb.append("\n  shot: ").append(Fmt.num(shot.distanceTo(v))).append(" blocks, launcher=")
                    .append(shot.launcher.isEmpty() ? "-" : shot.launcher.getItem().getRegistryName())
                    .append(" airborne=").append(shot.airborne).append(" mount=").append(shot.mount);
        }
        NBTTagCompound nbt = AnnouncedMobs.serialize(v);
        if (nbt != null) {
            sb.append("\n  nbt keys: ").append(nbt.getKeySet());
            sb.append("\n  ForgeData: ").append(nbt.getCompoundTag("ForgeData").getKeySet());
            NBTTagCompound caps = nbt.getCompoundTag("ForgeCaps");
            sb.append("\n  ForgeCaps:");
            if (caps.isEmpty()) {
                sb.append(" -");
            }
            for (String k : caps.getKeySet()) {
                String body = String.valueOf(caps.getTag(k));
                sb.append("\n    ").append(k).append(" = ").append(body.length() > 300 ? body.substring(0, 300) + "..." : body);
            }
        }
        report(sb.toString(), killTo);
    }

    // ---- helpers -----------------------------------------------------------------------

    private static String describe(@Nullable Entity e) {
        if (e == null) {
            return "-";
        }
        ResourceLocation id = EntityList.getKey(e);
        return e.getName() + " (" + (e instanceof EntityPlayer ? "player" : String.valueOf(id)) + ", "
                + e.getClass().getName() + ")";
    }

    private static String describe(DamageSource s) {
        StringBuilder sb = new StringBuilder();
        sb.append("type=").append(s.getDamageType()).append(" class=").append(s.getClass().getName());
        sb.append(" true=").append(describe(s.getTrueSource()));
        if (s.getImmediateSource() != s.getTrueSource()) {
            sb.append(" immediate=").append(describe(s.getImmediateSource()));
            if (s.getImmediateSource() != null && ShotTag.has(s.getImmediateSource())) {
                sb.append(" [warband:shot]");
            }
        }
        sb.append(" flags=");
        if (s.isProjectile()) sb.append("PROJ ");
        if (s.isExplosion()) sb.append("EXPL ");
        if (s.isFireDamage()) sb.append("FIRE ");
        if (s.isMagicDamage()) sb.append("MAGIC ");
        if (s.isUnblockable()) sb.append("BYPASS ");
        if (s.isDamageAbsolute()) sb.append("ABSOLUTE ");
        return sb.toString().trim();
    }

    private static void report(String text, @Nullable UUID to) {
        Warband.LOGGER.info(text);
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (to == null || server == null) {
            return;
        }
        EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(to);
        if (p != null) {
            for (String line : text.split("\n")) {
                p.sendMessage(Chat.text(line, TextFormatting.GRAY));
            }
        }
    }
}
