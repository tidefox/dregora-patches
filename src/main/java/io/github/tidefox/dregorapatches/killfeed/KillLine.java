package io.github.tidefox.dregorapatches.killfeed;

import java.util.Collection;
import java.util.Locale;

import io.github.tidefox.dregorapatches.util.Fmt;

/** Pure text rules of the one-line kill feed (docs/03 §2.6). Unit-tested. */
public final class KillLine {

    /** Names shorter than this match too much ("Ox" in "Oxford"). */
    static final int MIN_NAME = 3;

    private KillLine() {}

    /** Removes {@code §x} formatting codes and trims. */
    public static String strip(String s) {
        return s == null ? "" : s.replaceAll("(?i)§[0-9a-fk-or]", "").trim();
    }

    /**
     * Is {@code line} the kill line of this death? It names the victim (≥ {@value #MIN_NAME} chars) and,
     * if {@code credits} is non-empty, the killer or their pet. Scaling Health's {@code Blight <name> …}
     * line is left for a Blight: a split slime shares its parent's name.
     */
    static boolean matches(String line, Collection<String> names, boolean blight, Collection<String> credits) {
        String l = line.toLowerCase(Locale.ROOT);
        boolean named = false;
        for (String n : names) {
            if (n == null || n.length() < MIN_NAME) {
                continue;
            }
            String name = n.toLowerCase(Locale.ROOT);
            if (l.contains(name) && (blight || !l.contains("blight " + name))) {
                named = true;
                break;
            }
        }
        if (!named || credits.isEmpty()) {
            return named;
        }
        for (String k : credits) {
            if (k != null && !k.isEmpty() && l.contains(k.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** {@code " (4 Blight) in 18s"} after {@code "…and 11 more <mob> kills"}. */
    static String streakTail(int blights, long ticks) {
        return (blights > 0 ? " (" + blights + " Blight)" : "") + " in " + Fmt.duration(ticks);
    }
}
