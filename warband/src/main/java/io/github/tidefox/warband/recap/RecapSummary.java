package io.github.tidefox.warband.recap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/** Pure aggregation of a death's hits into the collapsed view (docs/05 §2.3). */
public final class RecapSummary {

    public static final class Source {
        public final String name;
        @Nullable
        public String entityNbt;
        @Nullable
        public String weaponName;
        @Nullable
        public String weaponNbt;
        public int count;
        public float taken;
        public float mitigated;
        public boolean allBypass = true;
        public boolean killingBlow;
        public long lastTick;

        Source(String name) {
            this.name = name;
        }
    }

    public final List<Hit> hits;
    /** By damage taken, descending; the killing blow's source is always included. */
    public final List<Source> sources;
    /** Sources beyond {@code maxSources}, summed. */
    public final int hiddenSources;
    public final float hiddenTaken;
    public final float totalTaken;
    public final float totalMitigated;
    public final float totalAbsorbed;
    public final float totalHealed;
    /** Ticks from the first hit to death. */
    public final long spanTicks;
    @Nullable
    public final Hit killingBlow;

    private RecapSummary(List<Hit> hits, List<Source> sources, int hiddenSources, float hiddenTaken,
            float totalTaken, float totalMitigated, float totalAbsorbed, float totalHealed, long spanTicks,
            @Nullable Hit killingBlow) {
        this.hits = hits;
        this.sources = sources;
        this.hiddenSources = hiddenSources;
        this.hiddenTaken = hiddenTaken;
        this.totalTaken = totalTaken;
        this.totalMitigated = totalMitigated;
        this.totalAbsorbed = totalAbsorbed;
        this.totalHealed = totalHealed;
        this.spanTicks = spanTicks;
        this.killingBlow = killingBlow;
    }

    /**
     * @param hits in time order (heals included); only those at or after
     *        {@code deathTick - windowTicks} are used
     */
    public static RecapSummary of(List<Hit> hits, long deathTick, long windowTicks, int maxSources) {
        List<Hit> used = new ArrayList<>();
        for (Hit h : hits) {
            if (h.tick >= deathTick - windowTicks && h.tick <= deathTick) {
                used.add(h);
            }
        }
        Hit kill = null;
        for (int i = used.size() - 1; i >= 0; i--) {
            if (!used.get(i).isHeal()) {
                kill = used.get(i);
                break;
            }
        }
        Map<String, Source> by = new LinkedHashMap<>();
        float taken = 0, mitigated = 0, absorbed = 0, healed = 0;
        long first = -1;
        for (Hit h : used) {
            if (h.isHeal()) {
                healed += h.raw;
                continue;
            }
            if (first < 0) {
                first = h.tick;
            }
            taken += h.taken();
            mitigated += h.mitigated();
            absorbed += h.absorbed;
            Source s = by.get(h.source);
            if (s == null) {
                s = new Source(h.source);
                by.put(h.source, s);
            }
            s.count++;
            s.taken += h.taken();
            s.mitigated += h.mitigated();
            s.allBypass &= h.is(Hit.BYPASS);
            s.lastTick = h.tick;
            if (h.sourceEntityNbt != null) {
                s.entityNbt = h.sourceEntityNbt;
            }
            if (h.weaponName != null) {
                s.weaponName = h.weaponName;
                s.weaponNbt = h.weaponNbt;
            }
            if (h == kill) {
                s.killingBlow = true;
                // The weapon that landed the killing blow wins over earlier ones.
                if (h.weaponName != null) {
                    s.weaponName = h.weaponName;
                    s.weaponNbt = h.weaponNbt;
                }
            }
        }
        List<Source> sorted = new ArrayList<>(by.values());
        Collections.sort(sorted, (a, b) -> Float.compare(b.taken, a.taken));
        int max = Math.max(1, maxSources);
        int hidden = 0;
        float hiddenTaken = 0;
        if (sorted.size() > max) {
            // Keep the killing blow's source visible even if it dealt little.
            List<Source> shown = new ArrayList<>(sorted.subList(0, max));
            Source killer = null;
            for (Source s : sorted) {
                if (s.killingBlow) {
                    killer = s;
                }
            }
            if (killer != null && !shown.contains(killer)) {
                shown.set(max - 1, killer);
            }
            for (Source s : sorted) {
                if (!shown.contains(s)) {
                    hidden++;
                    hiddenTaken += s.taken;
                }
            }
            sorted = shown;
        }
        long span = first < 0 ? 0 : deathTick - first;
        return new RecapSummary(used, sorted, hidden, hiddenTaken, taken, mitigated, absorbed, healed, span, kill);
    }
}
