package io.github.tidefox.dregorapatches.util;

import java.util.Locale;

/** Pure text helpers (no Minecraft classes), unit-tested. */
public final class Fmt {

    private Fmt() {}

    /** One decimal, dot separator regardless of server locale. */
    public static String num(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /** Whole blocks, e.g. {@code 97m}. */
    public static String meters(double v) {
        return String.format(Locale.ROOT, "%dm", (long) Math.floor(v));
    }

    /** Seconds with one decimal, e.g. {@code 2.4s}. */
    public static String seconds(long ticks) {
        return num(ticks / 20.0) + "s";
    }

    /**
     * Dot leader between a label and a right-hand value so that {@code label + leader}
     * is at least {@code width} characters: {@code "Wyvern ×3 " + "......"}. Chat uses a
     * proportional font, so this only approximates a column.
     */
    public static String leader(String label, int width) {
        return leader(label.length(), width);
    }

    /** {@link #leader(String, int)} for a label of {@code used} characters. */
    public static String leader(int used, int width) {
        int n = Math.max(2, width - used);
        StringBuilder sb = new StringBuilder(n + 1);
        sb.append(' ');
        for (int i = 0; i < n; i++) {
            sb.append('.');
        }
        return sb.append(' ').toString();
    }

    /** {@code fall → Fall}, {@code inFire → In fire}, {@code lightningBolt → Lightning bolt}. */
    public static String humanize(String camel) {
        if (camel == null || camel.isEmpty()) {
            return "Unknown";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (c == '_' || c == '.') {
                sb.append(' ');
            } else if (Character.isUpperCase(c) && i > 0 && sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                sb.append(' ').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
        return sb.toString();
    }
}
