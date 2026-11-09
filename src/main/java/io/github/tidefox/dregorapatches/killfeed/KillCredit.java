package io.github.tidefox.dregorapatches.killfeed;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityOwnable;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;

/** Which player gets credit for a hit or a kill (docs/03 §2.2). Vanilla interfaces only. */
public final class KillCredit {

    public enum Kind { DIRECT, PET, LINGERING }

    public final EntityPlayerMP player;
    public final Kind kind;
    /** The tamed mob that dealt the blow, for {@link Kind#PET}. */
    @Nullable
    public final Entity pet;

    private KillCredit(EntityPlayerMP player, Kind kind, @Nullable Entity pet) {
        this.player = player;
        this.kind = kind;
        this.pet = pet;
    }

    /** The true source if it is a player other than the victim (own-arrow / self-damage gives no credit). */
    @Nullable
    public static EntityPlayerMP killer(EntityLivingBase victim, DamageSource src) {
        Entity t = src.getTrueSource();
        return t instanceof EntityPlayerMP && t != victim ? (EntityPlayerMP) t : null;
    }

    /** Owner of a tamed true source (wolf, tamed dragon, …), if online and not the victim. */
    @Nullable
    public static EntityPlayerMP owner(EntityLivingBase victim, DamageSource src, MinecraftServer server) {
        Entity t = src.getTrueSource();
        if (!(t instanceof IEntityOwnable) || t instanceof EntityPlayerMP) {
            return null;
        }
        UUID id = ((IEntityOwnable) t).getOwnerId();
        EntityPlayerMP p = id == null ? null : server.getPlayerList().getPlayerByUUID(id);
        return p != null && p != victim ? p : null;
    }

    /**
     * Direct kill, else a pet's kill (if {@code pets}), else, for a death with no attacker (fire,
     * bleeding, a fall), the player who landed the last hit within {@code lingerTicks}. Null = no player.
     */
    @Nullable
    public static KillCredit of(EntityLivingBase victim, DamageSource src, @Nullable Fight fight, long now,
            boolean pets, long lingerTicks, MinecraftServer server) {
        EntityPlayerMP p = killer(victim, src);
        if (p != null) {
            return new KillCredit(p, Kind.DIRECT, null);
        }
        if (pets) {
            p = owner(victim, src, server);
            if (p != null) {
                return new KillCredit(p, Kind.PET, src.getTrueSource());
            }
        }
        // Only for deaths nobody dealt (fire, bleeding, fall, poison): a zombie's kill stays the zombie's.
        String last = fight == null || lingerTicks <= 0 || src.getTrueSource() != null
                ? null : fight.recentAttacker(now, lingerTicks);
        if (last != null) {
            try {
                p = server.getPlayerList().getPlayerByUUID(UUID.fromString(last));
            } catch (IllegalArgumentException e) {
                p = null;
            }
            if (p != null && p != victim) {
                return new KillCredit(p, Kind.LINGERING, null);
            }
        }
        return null;
    }
}
