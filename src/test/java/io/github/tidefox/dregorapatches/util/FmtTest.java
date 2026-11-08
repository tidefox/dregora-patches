package io.github.tidefox.dregorapatches.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FmtTest {

    @Test
    void numbersUseDotAndOneDecimal() {
        assertEquals("46.2", Fmt.num(46.249));
        assertEquals("0.0", Fmt.num(0));
        assertEquals("97m", Fmt.meters(97.9));
        assertEquals("2.4s", Fmt.seconds(48));
    }

    @Test
    void leaderPadsToWidthWithAtLeastTwoDots() {
        assertEquals(" ........ ", Fmt.leader("Wyvern ×3", 17));
        assertEquals(" .. ", Fmt.leader("a very long source name", 10));
    }

    @Test
    void humanize() {
        assertEquals("Fall", Fmt.humanize("fall"));
        assertEquals("In fire", Fmt.humanize("inFire"));
        assertEquals("Mod damage", Fmt.humanize("mod_damage"));
        assertEquals("Unknown", Fmt.humanize(""));
    }
}
