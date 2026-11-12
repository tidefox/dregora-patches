package io.github.tidefox.dregorapatches.killfeed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.minecraft.util.text.TextFormatting;

class BadgesTest {

    @Test
    void infernalRanksMatchInfernalMobs() {
        assertEquals(1, Badges.infernalRank(1));
        assertEquals(1, Badges.infernalRank(5));
        assertEquals(2, Badges.infernalRank(6));
        assertEquals(2, Badges.infernalRank(10));
        assertEquals(3, Badges.infernalRank(11));
        assertEquals("Ultra", Badges.infernalName(2));
        assertEquals(TextFormatting.YELLOW, Badges.infernalColor(2));
    }

    @Test
    void championColorsFollowThePackRanks() {
        assertEquals(TextFormatting.YELLOW, Badges.championColor(1));
        assertEquals(TextFormatting.AQUA, Badges.championColor(3));
        assertEquals(TextFormatting.RED, Badges.championColor(5));
        assertEquals(TextFormatting.RED, Badges.championColor(7));
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
