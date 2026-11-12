package io.github.tidefox.dregorapatches.killfeed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

class ChatLinesTest {

    @Test
    void claimsLineNamingTheVictimOnce() {
        ChatLines l = new ChatLines();
        l.add(10, "Blight Xaphan Lvl 4 was killed by Crossfire!", null);
        assertEquals("Blight Xaphan Lvl 4 was killed by Crossfire!", l.claim(10, Collections.singleton("xaphan lvl 4"), true));
        assertNull(l.claim(10, Collections.singleton("Xaphan Lvl 4"), true), "a line serves one kill");
    }

    @Test
    void perPlayerCopiesCountOnce() {
        ChatLines l = new ChatLines();
        l.add(5, "Bob was slain by Alice", "p1");
        l.add(5, "Bob was slain by Alice", "p2");
        assertEquals(1, l.size());
        l.add(6, "Bob was slain by Alice", "p1");
        assertEquals(2, l.size(), "same text on a later tick is a new line");
    }

    @Test
    void sameTextTwiceToOnePlayerIsTwoKills() {
        ChatLines l = new ChatLines();
        // Scaling Health: Blight 1 → p1, p2; Blight 2 → p1, p2 (same name, same tick)
        for (int i = 0; i < 2; i++) {
            l.add(5, "Blight Defiled Slime was killed by JCRVIII!", "p1");
            l.add(5, "Blight Defiled Slime was killed by JCRVIII!", "p2");
        }
        assertEquals(2, l.size());
        l.add(5, "Fire Dragon was slain by Alice", null);
        l.add(5, "Fire Dragon was slain by Alice", null);
        assertEquals(4, l.size(), "two broadcasts are two lines");
    }

    @Test
    void blightLineIsLeftForTheBlight() {
        ChatLines l = new ChatLines();
        l.add(5, "Blight Defiled Slime was killed by JCRVIII!", null);
        assertNull(l.claim(5, Collections.singleton("Defiled Slime"), false), "plain split slime, same name");
        assertEquals("Blight Defiled Slime was killed by JCRVIII!", l.claim(5, Collections.singleton("Defiled Slime"), true));
    }

    @Test
    void ignoresOlderLinesShortNamesAndOtherMobs() {
        ChatLines l = new ChatLines();
        l.add(3, "Fire Dragon was slain by Alice", null);
        assertNull(l.claim(4, Collections.singleton("Fire Dragon"), false), "sent before the death");
        l.add(4, "Ox was slain by Alice", null);
        assertNull(l.claim(4, Collections.singleton("Ox"), false), "too short to match safely");
        assertNull(l.claim(4, Arrays.asList("Ice Dragon", "Zombie"), false));
    }

    @Test
    void pruneDropsOldLines() {
        ChatLines l = new ChatLines();
        l.add(1, "a line", null);
        l.add(9, "another line", null);
        l.prune(5);
        assertEquals(1, l.size());
        l.add(9, " ", null);
        assertEquals(1, l.size(), "blank lines are ignored");
    }
}
