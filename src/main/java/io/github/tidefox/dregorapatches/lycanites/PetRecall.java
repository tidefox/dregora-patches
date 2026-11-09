package io.github.tidefox.dregorapatches.lycanites;

import io.github.tidefox.dregorapatches.DregoraPatches;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

/** Counters and log line for {@code MixinPetEntry} (docs/09). No Lycanites classes here. */
public final class PetRecall {

    private static int recalled;
    private static int failed;

    private PetRecall() {}

    public static void recalled(EntityLivingBase owner, Entity pet, boolean ok) {
        if (ok) recalled++; else failed++;
        DregoraPatches.LOGGER.info("Lycanites: {} unloaded pet {} for {} ({})",
                ok ? "recalled" : "could not respawn", pet.getName(), owner.getName(),
                ok ? "respawned at owner" : "spawn failed");
    }

    public static int recalledCount() {
        return recalled;
    }

    public static int failedCount() {
        return failed;
    }
}
