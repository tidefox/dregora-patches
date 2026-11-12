package io.github.tidefox.dregorapatches.killfeed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BadgesTest {

    @Test
    void infernalRanksMatchInfernalMobs() {
        assertEquals("Rare", Badges.infernalRank(1));
        assertEquals("Rare", Badges.infernalRank(5));
        assertEquals("Ultra", Badges.infernalRank(6));
        assertEquals("Ultra", Badges.infernalRank(10));
        assertEquals("Infernal", Badges.infernalRank(11));
    }

    @Test
    void dragonStageMatchesIceAndFire() {
        assertEquals(1, Badges.dragonStage(0));
        assertEquals(1, Badges.dragonStage(24));
        assertEquals(2, Badges.dragonStage(25));
        assertEquals(3, Badges.dragonStage(50));
        assertEquals(4, Badges.dragonStage(99));
        assertEquals(5, Badges.dragonStage(125));
    }

    @Test
    void stars() {
        assertEquals("★★★", Badges.stars(3));
        assertEquals("", Badges.stars(0));
    }
}
