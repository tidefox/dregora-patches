package io.github.tidefox.dregorapatches.killfeed;

import io.github.tidefox.dregorapatches.util.Fmt;

/** The gray tail of the follow-up line, e.g. {@code " · 97m · final 22.0 · 1m12s"}. Pure, unit-tested. */
public final class KillLine {

    /** Fights shorter than this (one-shots) show no duration. */
    static final long MIN_DURATION_TICKS = 20;

    private KillLine() {}

    /** Removes {@code §x} formatting codes and trims. */
    public static String strip(String s) {
        return s == null ? "" : s.replaceAll("(?i)\u00a7[0-9a-fk-or]", "").trim();
    }

    /** Our own kill line after the victim's name (docs/03 §2.5). */
    public static String verb(KillCredit.Kind kind, String killer, String pet) {
        switch (kind) {
            case PET:
                return " was slain by " + killer + "'s " + pet;
            case LINGERING:
                return " died fighting " + killer;
            default:
                return " was slain by " + killer;
        }
    }

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
