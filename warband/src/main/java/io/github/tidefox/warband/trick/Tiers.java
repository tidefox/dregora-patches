package io.github.tidefox.warband.trick;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

/** Distance tiers from config ({@code "Sniper=96"}). Pure, unit-tested. */
public final class Tiers {

    public static final class Tier {
        public final String name;
        public final double distance;

        Tier(String name, double distance) {
            this.name = name;
            this.distance = distance;
        }
    }

    private final List<Tier> tiers;

    private Tiers(List<Tier> tiers) {
        this.tiers = tiers;
    }

    /** Malformed entries are skipped. Sorted by distance ascending. */
    public static Tiers parse(String[] entries) {
        List<Tier> out = new ArrayList<>();
        for (String e : entries) {
            int eq = e == null ? -1 : e.lastIndexOf('=');
            if (eq <= 0) {
                continue;
            }
            try {
                double d = Double.parseDouble(e.substring(eq + 1).trim());
                out.add(new Tier(e.substring(0, eq).trim(), d));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        Collections.sort(out, (a, b) -> Double.compare(a.distance, b.distance));
        return new Tiers(out);
    }

    /** Highest tier reached, or null when below every tier. */
    @Nullable
    public Tier of(double distance) {
        Tier best = null;
        for (Tier t : tiers) {
            if (distance >= t.distance) {
                best = t;
            }
        }
        return best;
    }
}
