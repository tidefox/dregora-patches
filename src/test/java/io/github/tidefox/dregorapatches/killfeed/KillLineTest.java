package io.github.tidefox.dregorapatches.killfeed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class KillLineTest {

    private static final List<String> SLIME = Collections.singletonList("Defiled Slime");
    private static final List<String> BY = Collections.singletonList("JCRVIII");
    private static final String BLIGHT_LINE = "Blight Defiled Slime was killed by JCRVIII!";

    @Test
    void stripRemovesFormattingCodes() {
        assertEquals("Blight Xaphan Lvl 4", KillLine.strip("§cBlight §rXaphan Lvl 4 "));
    }

    @Test
    void blightLineIsLeftForTheBlight() {
        assertFalse(KillLine.matches(BLIGHT_LINE, SLIME, false, BY), "plain split slime, same name");
        assertTrue(KillLine.matches(BLIGHT_LINE, SLIME, true, BY));
        assertTrue(KillLine.matches("Defiled Slime was slain by JCRVIII", SLIME, false, BY));
    }

    @Test
    void lineMustNameTheKillerOrPet() {
        assertFalse(KillLine.matches("Bob was slain by Carol", Collections.singletonList("Bob"), false, Collections.singletonList("Alice")));
        assertTrue(KillLine.matches("Blight Spider was killed by Wolf!", Collections.singletonList("Spider"), true,
                Arrays.asList("Alice", "Wolf")));
        assertTrue(KillLine.matches("Zombie burned to death", Collections.singletonList("Zombie"), false,
                Collections.<String>emptyList()), "lingering credit: the line may not name the player");
    }

    @Test
    void shortNamesAndOtherMobsDontMatch() {
        assertFalse(KillLine.matches("Ox was slain by Alice", Collections.singletonList("Ox"), false, Collections.<String>emptyList()));
        assertFalse(KillLine.matches("Fire Dragon was slain by Alice", Arrays.asList("Ice Dragon", "Zombie"), false,
                Collections.<String>emptyList()));
        assertTrue(KillLine.matches("FIRE DRAGON was slain by alice", Collections.singletonList("Fire Dragon"), false,
                Collections.singletonList("Alice")), "case-insensitive");
    }

    @Test
    void streakTail() {
        assertEquals(" (4 Blight) in 18s", KillLine.streakTail(4, 18 * 20));
        assertEquals(" in 1m05s", KillLine.streakTail(0, 65 * 20));
    }
}
