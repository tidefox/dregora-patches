package io.github.tidefox.dregorapatches.killfeed;

/**
 * Player hits on one victim (docs/03 §2.3). Pure: ticks in, no Minecraft classes. A gap longer
 * than the timeout starts a new fight.
 */
public final class Fight {

    private long start = -1, last = -1;
    private float lastAmount;

    /** Records a player hit at {@code now} (world ticks). */
    public void hit(long now, float amount, long timeoutTicks) {
        if (start < 0 || now - last > timeoutTicks) {
            start = now;
        }
        last = now;
        lastAmount = amount;
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
}
