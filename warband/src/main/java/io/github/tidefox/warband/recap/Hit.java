package io.github.tidefox.warband.recap;

import javax.annotation.Nullable;

/**
 * One recorded hit (or heal) on a player. Plain data so the aggregation is unit-testable:
 * hovers are kept as their NBT payload strings.
 */
public final class Hit {

    public static final int PROJ = 1, EXPL = 2, FIRE = 4, MAGIC = 8, BYPASS = 16, HEAL = 32, ESTIMATED = 64;

    /** Server tick counter. */
    public final long tick;
    public final String damageType;
    /** Display name of the true source, or an environment label ("Fall", "Poison"). */
    public final String source;
    /** {@code SHOW_ENTITY} payload, null for environment. */
    @Nullable
    public final String sourceEntityNbt;
    @Nullable
    public final String weaponName;
    /** {@code SHOW_ITEM} payload. */
    @Nullable
    public final String weaponNbt;
    /** Amount at {@code LivingHurtEvent}, before armor and potions. */
    public final float raw;
    /** Health lost (after armor, potions and absorption). */
    public final float hpLoss;
    /** Absorption hearts this hit used up. */
    public final float absorbed;
    public final float healthBefore;
    public final float absorptionBefore;
    public final int flags;

    public Hit(long tick, String damageType, String source, @Nullable String sourceEntityNbt,
            @Nullable String weaponName, @Nullable String weaponNbt, float raw, float hpLoss, float absorbed,
            float healthBefore, float absorptionBefore, int flags) {
        this.tick = tick;
        this.damageType = damageType;
        this.source = source;
        this.sourceEntityNbt = sourceEntityNbt;
        this.weaponName = weaponName;
        this.weaponNbt = weaponNbt;
        this.raw = raw;
        this.hpLoss = hpLoss;
        this.absorbed = absorbed;
        this.healthBefore = healthBefore;
        this.absorptionBefore = absorptionBefore;
        this.flags = flags;
    }

    public static Hit heal(long tick, float amount, float healthBefore) {
        return new Hit(tick, "heal", "Heal", null, null, null, amount, 0, 0, healthBefore, 0, HEAL);
    }

    public boolean is(int flag) {
        return (flags & flag) != 0;
    }

    public boolean isHeal() {
        return is(HEAL);
    }

    /** Everything this hit took: health plus absorption. */
    public float taken() {
        return hpLoss + absorbed;
    }

    /** What armor, enchantments and potions removed. Never negative. */
    public float mitigated() {
        return isHeal() ? 0 : Math.max(0, raw - taken());
    }

    public float healthAfter() {
        return isHeal() ? healthBefore + raw : Math.max(0, healthBefore - hpLoss);
    }

    /** Damage beyond what was left. */
    public float overkill() {
        return Math.max(0, taken() - healthBefore - absorptionBefore);
    }

    /** Grouping key for the collapsed view: same source name and weapon. */
    String groupKey() {
        return source + "\u0000" + (weaponName == null ? "" : weaponName);
    }
}
