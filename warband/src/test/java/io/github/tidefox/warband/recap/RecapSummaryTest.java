package io.github.tidefox.warband.recap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class RecapSummaryTest {

    private static Hit hit(long tick, String src, String weapon, float raw, float hp, float abs, float before,
            int flags) {
        return new Hit(tick, "mob", src, null, weapon, weapon == null ? null : "{id:\"x\"}", raw, hp, abs, before, 0,
                flags);
    }

    @Test
    void groupsBySourceAndSortsByDamage() {
        List<Hit> hits = Arrays.asList(
                hit(100, "Wyvern", null, 9.5f, 6.1f, 0, 40, 0),
                hit(110, "Poison", null, 1.5f, 1.5f, 0, 33.9f, Hit.BYPASS | Hit.MAGIC),
                hit(120, "Wyvern", null, 9.5f, 6.1f, 0, 32.4f, 0),
                hit(130, "Poison", null, 1.5f, 1.5f, 0, 24.8f, Hit.BYPASS | Hit.MAGIC),
                hit(236, "Skeleton", "Bow", 12f, 12f, 0, 12f, Hit.PROJ));
        RecapSummary s = RecapSummary.of(hits, 236, 200, 5);
        assertEquals(3, s.sources.size());
        assertEquals("Wyvern", s.sources.get(0).name);
        assertEquals(2, s.sources.get(0).count);
        assertEquals(12.2f, s.sources.get(0).taken, 1e-4);
        assertEquals(6.8f, s.sources.get(0).mitigated, 1e-4);
        assertTrue(s.sources.get(2).allBypass);
        assertTrue(s.sources.get(1).killingBlow);
        assertEquals("Bow", s.sources.get(1).weaponName);
        assertSame(hits.get(4), s.killingBlow);
        assertEquals(27.2f, s.totalTaken, 1e-4);
        assertEquals(136, s.spanTicks);
    }

    @Test
    void windowDropsOldHits() {
        List<Hit> hits = Arrays.asList(hit(0, "Zombie", null, 3, 3, 0, 20, 0), hit(300, "Fall", null, 5, 5, 0, 5, 0));
        RecapSummary s = RecapSummary.of(hits, 300, 200, 5);
        assertEquals(1, s.sources.size());
        assertEquals("Fall", s.sources.get(0).name);
        assertEquals(1, s.hits.size());
        assertNull(s.sources.get(0).weaponName);
    }

    @Test
    void killingBlowSourceStaysVisibleWhenTrimmed() {
        List<Hit> hits = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            hits.add(hit(i, "Mob" + i, null, 10 + i, 10 + i, 0, 100, 0));
        }
        hits.add(hit(10, "Cactus", null, 1, 1, 0, 1, 0));
        RecapSummary s = RecapSummary.of(hits, 10, 200, 3);
        assertEquals(3, s.sources.size());
        assertEquals("Cactus", s.sources.get(2).name);
        assertEquals(4, s.hiddenSources);
        assertEquals(10 + 11 + 12 + 13, s.hiddenTaken, 1e-4);
    }

    @Test
    void healsCountSeparatelyAndNeverKill() {
        List<Hit> hits = Arrays.asList(hit(10, "Zombie", null, 4, 4, 0, 20, 0), Hit.heal(12, 2, 16));
        RecapSummary s = RecapSummary.of(hits, 15, 200, 5);
        assertEquals(2f, s.totalHealed, 1e-4);
        assertEquals("Zombie", s.killingBlow.source);
        assertEquals(1, s.sources.size());
    }

    @Test
    void noHitsMeansNoKillingBlow() {
        RecapSummary s = RecapSummary.of(new ArrayList<Hit>(), 10, 200, 5);
        assertNull(s.killingBlow);
        assertTrue(s.sources.isEmpty());
    }

    @Test
    void absorptionAndOverkill() {
        Hit h = new Hit(0, "mob", "Golem", null, null, null, 30, 10, 4, 10, 4, 0);
        assertEquals(14f, h.taken(), 1e-4);
        assertEquals(16f, h.mitigated(), 1e-4);
        assertEquals(0f, h.overkill(), 1e-4);
        assertEquals(0f, h.healthAfter(), 1e-4);
        Hit big = new Hit(0, "mob", "Golem", null, null, null, 30, 25, 0, 10, 0, 0);
        assertEquals(15f, big.overkill(), 1e-4);
        assertFalse(big.isHeal());
    }

    @Test
    void timelineLines() {
        List<Hit> hits = Arrays.asList(hit(100, "Wyvern", null, 9.5f, 6.1f, 0, 37.5f, 0),
                hit(236, "Skeleton", "Bow", 12f, 12f, 0, 12f, Hit.PROJ));
        RecapSummary s = RecapSummary.of(hits, 236, 200, 5);
        assertEquals("-6.8s Wyvern 9.5→6.1 (HP 31.4)\n-0.0s Skeleton [Bow] 12.0→12.0 (HP 0.0)",
                RecapFormatter.timeline(s, 236));
    }
}
