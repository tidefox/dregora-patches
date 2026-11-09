package io.github.tidefox.dregorapatches.killfeed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FightTest {

    private static final long TIMEOUT = 1200;

    @Test
    void durationRunsFromFirstHit() {
        Fight f = new Fight();
        f.hit(100, 5, TIMEOUT);
        f.hit(500, 7, TIMEOUT);
        f.hit(1540, 22, TIMEOUT);
        assertEquals(1440, f.duration(1540, TIMEOUT));
        assertEquals(22f, f.finalHit(1540));
    }

    @Test
    void gapLongerThanTimeoutStartsNewFight() {
        Fight f = new Fight();
        f.hit(100, 5, TIMEOUT);
        f.hit(100 + TIMEOUT + 1, 9, TIMEOUT);
        assertEquals(0, f.duration(100 + TIMEOUT + 1, TIMEOUT));
        assertEquals(-1, f.duration(100 + 3 * TIMEOUT, TIMEOUT));
    }

    @Test
    void finalHitOnlyIfItLandedThisTick() {
        Fight f = new Fight();
        assertEquals(-1, f.duration(10, TIMEOUT));
        f.hit(10, 4, TIMEOUT);
        assertTrue(Float.isNaN(f.finalHit(11)));
    }

    @Test
    void recentAttackerWithinWindow() {
        Fight f = new Fight();
        f.hit(100, 5, TIMEOUT, "alice");
        f.hit(150, 5, TIMEOUT, "bob");
        assertEquals("bob", f.recentAttacker(250, 200));
        assertNull(f.recentAttacker(351, 200));
    }
}
