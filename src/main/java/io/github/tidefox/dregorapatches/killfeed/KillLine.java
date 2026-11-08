package io.github.tidefox.dregorapatches.killfeed;

import io.github.tidefox.dregorapatches.util.Fmt;

/** The gray tail of the follow-up line, e.g. {@code " · 97m · final 22.0 · 1m12s"}. Pure, unit-tested. */
public final class KillLine {

    /** Fights shorter than this (one-shots) show no duration. */
    static final long MIN_DURATION_TICKS = 20;

    private KillLine() {}

    /**
     * @param distance blocks from the shot's origin, or NaN (not a tagged shot / hidden)
     * @param finalHit damage of the killing hit, or NaN (unknown / hidden)
     * @param duration fight length in ticks, or negative (unknown / hidden)
     */
    public static String tail(double distance, double finalHit, long duration) {
        StringBuilder sb = new StringBuilder();
        if (!Double.isNaN(distance)) {
            sb.append(" · ").append(Fmt.meters(distance));
        }
        if (!Double.isNaN(finalHit)) {
            sb.append(" · final ").append(Fmt.num(finalHit));
        }
        if (duration >= MIN_DURATION_TICKS) {
            sb.append(" · ").append(Fmt.duration(duration));
        }
        return sb.toString();
    }
}
