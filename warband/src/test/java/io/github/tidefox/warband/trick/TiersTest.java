package io.github.tidefox.warband.trick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class TiersTest {

    private final Tiers t = Tiers.parse(new String[] {"Sniper=96", "Nice shot=40", "Long shot = 64", "bad", "x=y",
            "Legendary=128"});

    @Test
    void picksHighestReached() {
        assertNull(t.of(39.9));
        assertEquals("Nice shot", t.of(40).name);
        assertEquals("Long shot", t.of(95.99).name);
        assertEquals("Sniper", t.of(97).name);
        assertEquals("Legendary", t.of(500).name);
    }
}
