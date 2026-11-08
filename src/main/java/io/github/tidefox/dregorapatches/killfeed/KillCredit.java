package io.github.tidefox.dregorapatches.killfeed;

import javax.annotation.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;

/** Which player gets credit for a hit or a kill (docs/03 §2.2). */
public final class KillCredit {

    private KillCredit() {}

    /** The true source if it is a player other than the victim (own-arrow / self-damage gives no credit). */
    @Nullable
    public static EntityPlayerMP killer(EntityLivingBase victim, DamageSource src) {
        Entity t = src.getTrueSource();
        return t instanceof EntityPlayerMP && t != victim ? (EntityPlayerMP) t : null;
    }
}
