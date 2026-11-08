package io.github.tidefox.warband.trick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class RecordBookTest {

    private static RecordBook.Entry e(String who, double d) {
        return new RecordBook.Entry("uuid-" + who, who, d, "Ice Dragon", 0);
    }

    @Test
    void personalBestAndServerRecord() {
        RecordBook b = new RecordBook();
        RecordBook.Result r = b.submit(e("Alice", 71));
        assertTrue(r.personalBest);
        assertNull(r.previousBest);
        assertTrue(r.serverRecord);
        assertEquals("first trick shot", TrickShotListener.recordLine(r));

        r = b.submit(e("Alice", 97));
        assertTrue(r.personalBest);
        assertEquals(71, r.previousBest.distance, 0);
        assertEquals("new server record (prev 71m by Alice)", TrickShotListener.recordLine(r));

        r = b.submit(e("Bob", 50));
        assertTrue(r.personalBest);
        assertFalse(r.serverRecord);
        assertEquals(3, r.rank);

        r = b.submit(e("Bob", 60));
        assertEquals("new personal best (prev 50m)", TrickShotListener.recordLine(r));

        r = b.submit(e("Bob", 55));
        assertFalse(r.personalBest);
        assertNull(TrickShotListener.recordLine(r));
        assertEquals(60, b.bestByName("bob").distance, 0);
    }

    @Test
    void topListKeepsTen() {
        RecordBook b = new RecordBook();
        for (int i = 1; i <= 12; i++) {
            b.submit(e("P" + i, i * 10));
        }
        assertEquals(10, b.top().size());
        assertEquals(120, b.top().get(0).distance, 0);
        assertEquals(30, b.top().get(9).distance, 0);
        assertEquals(0, b.submit(e("Low", 5)).rank);
    }

    @Test
    void restoreSortsAndTrims() {
        RecordBook b = new RecordBook();
        b.restore(new ArrayList<>(Arrays.asList(e("A", 40))), Arrays.asList(e("A", 40), e("B", 90)));
        assertEquals("B", b.top().get(0).player);
        assertEquals(40, b.bestOf("uuid-A").distance, 0);
    }
}
