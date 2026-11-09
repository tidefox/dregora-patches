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
        l.add(10, "Blight Xaphan Lvl 4 was killed by Crossfire!");
        assertEquals("Blight Xaphan Lvl 4 was killed by Crossfire!", l.claim(10, Collections.singleton("xaphan lvl 4")));
        assertNull(l.claim(10, Collections.singleton("Xaphan Lvl 4")), "a line serves one kill");
    }

    @Test
    void perPlayerCopiesCountOnce() {
        ChatLines l = new ChatLines();
        l.add(5, "Bob was slain by Alice");
        l.add(5, "Bob was slain by Alice");
        assertEquals(1, l.size());
        l.add(6, "Bob was slain by Alice");
        assertEquals(2, l.size(), "same text on a later tick is a new line");
    }

    @Test
    void ignoresOlderLinesShortNamesAndOtherMobs() {
        ChatLines l = new ChatLines();
        l.add(3, "Fire Dragon was slain by Alice");
        assertNull(l.claim(4, Collections.singleton("Fire Dragon")), "sent before the death");
        l.add(4, "Ox was slain by Alice");
        assertNull(l.claim(4, Collections.singleton("Ox")), "too short to match safely");
        assertNull(l.claim(4, Arrays.asList("Ice Dragon", "Zombie")));
    }

    @Test
    void pruneDropsOldLines() {
        ChatLines l = new ChatLines();
        l.add(1, "a line");
        l.add(9, "another line");
        l.prune(5);
        assertEquals(1, l.size());
        l.add(9, " ");
        assertEquals(1, l.size(), "blank lines are ignored");
    }
}
