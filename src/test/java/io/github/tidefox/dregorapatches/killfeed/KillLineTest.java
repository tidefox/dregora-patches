package io.github.tidefox.dregorapatches.killfeed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class KillLineTest {

    @Test
    void allParts() {
        assertEquals(" · 97m · final 22.0 · 1m12s", KillLine.tail(97.6, 22, 72 * 20));
    }

    @Test
    void meleeOneShotHasNoDistanceOrDuration() {
        assertEquals(" · final 18.4", KillLine.tail(Double.NaN, 18.4, 3));
    }

    @Test
    void nothingKnown() {
        assertEquals("", KillLine.tail(Double.NaN, Double.NaN, -1));
    }
}
