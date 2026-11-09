package io.github.tidefox.dregorapatches.killfeed;

import javax.annotation.Nullable;

/**
 * Player hits on one victim (docs/03 §2.3). Pure: ticks in, no Minecraft classes. A gap longer
 * than the timeout starts a new fight. Remembers who landed the last hit, so a kill by fire,
 * bleeding or a fall right after still has a player to credit (§2.2).
 */
public final class Fight {

    private long start = -1, last = -1;
    private float lastAmount;
    @Nullable
    private String lastAttacker;

    /** Records a player hit at {@code now} (world ticks). */
    public void hit(long now, float amount, long timeoutTicks) {
        hit(now, amount, timeoutTicks, null);
    }

    /** As {@link #hit(long, float, long)}, remembering the attacker (player UUID string). */
    public void hit(long now, float amount, long timeoutTicks, @Nullable String attacker) {
        if (start < 0 || now - last > timeoutTicks) {
            start = now;
        }
        last = now;
        lastAmount = amount;
        lastAttacker = attacker;
    }

    /** Ticks from the first hit of this fight to {@code now}, or -1 if the fight timed out / never started. */
    public long duration(long now, long timeoutTicks) {
        if (start < 0 || now - last > timeoutTicks) {
            return -1;
        }
        return Math.max(0, now - start);
    }

    /** Damage of the most recent player hit, if it landed at {@code now}; else NaN. */
    public float finalHit(long now) {
        return last == now ? lastAmount : Float.NaN;
    }

    /** Who landed the last hit, if it was at most {@code windowTicks} before {@code now}; else null. */
    @Nullable
    public String recentAttacker(long now, long windowTicks) {
        return last >= 0 && now - last <= windowTicks ? lastAttacker : null;
    }
}
