package io.github.tidefox.dregorapatches.reactor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClaimBoxTest {

    @Test
    void boxIsInclusiveOnAllAxes() {
        assertTrue(ClaimBox.contains(0, 64, 0, 26, 26, 38, -26));
        assertFalse(ClaimBox.contains(0, 64, 0, 26, 27, 64, 0));
        assertFalse(ClaimBox.contains(0, 64, 0, 26, 0, 91, 0));
        assertFalse(ClaimBox.contains(0, 64, 0, 26, 0, 64, -27));
    }

    @Test
    void chunkRangeHandlesNegativeCoords() {
        // x -1650 ± 26 → -1676..-1624 → chunks -105..-102; z 20 ± 26 → -6..46 → chunks -1..2
        assertArrayEquals(new int[] {-105, -1, -102, 2}, ClaimBox.chunkRange(-1650, 20, 26));
    }
}
