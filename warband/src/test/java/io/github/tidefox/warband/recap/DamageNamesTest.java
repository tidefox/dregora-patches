package io.github.tidefox.warband.recap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DamageNamesTest {

    @Test
    void knownAndUnknownTypes() {
        assertEquals("Fall", DamageNames.environment("fall", false));
        assertEquals("Wither effect", DamageNames.environment("wither", false));
        assertEquals("Some mod thing", DamageNames.environment("someModThing", false));
    }

    @Test
    void magicWhilePoisonedIsPoison() {
        assertEquals("Poison", DamageNames.environment("magic", true));
        assertEquals("Magic", DamageNames.environment("magic", false));
    }
}
