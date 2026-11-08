package io.github.tidefox.dregorapatches.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CoordFormatTest {

    @Test
    void roundsToNearestStepSymmetrically() {
        assertEquals(-1700, CoordFormat.estimate(-1650, 100));
        assertEquals(-1600, CoordFormat.estimate(-1649, 100));
        assertEquals(0, CoordFormat.estimate(20, 100));
        assertEquals(100, CoordFormat.estimate(50, 100));
        assertEquals(-100, CoordFormat.estimate(-50, 100));
        assertEquals(-2800, CoordFormat.estimate(-2804, 100));
        assertEquals(-2500, CoordFormat.estimate(-2531, 100));
        assertEquals(17, CoordFormat.estimate(17, 1));
    }

    @Test
    void estimatedHasNoY() {
        assertEquals("≈ -1700, 0", CoordFormat.estimated(-1650, 20, 100));
        assertEquals("-1650 11 20", CoordFormat.exact(-1650, 11, 20));
    }
}
