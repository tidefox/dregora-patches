package io.github.tidefox.warband.recap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class RecapStoreTest {

    @Test
    void ringBufferAndSnapshot() {
        RecapStore st = new RecapStore();
        UUID id = UUID.randomUUID();
        assertNull(st.lastDeath(id));
        for (int i = 0; i < 25; i++) {
            st.add(id, Hit.heal(i, 1, 1), 20);
        }
        RecapStore.Death d = st.snapshot(id, 30, "Fall");
        assertEquals(20, d.hits.size());
        assertEquals(5, d.hits.get(0).tick);
        // Live buffer is consumed by the snapshot; a death with no hits still snapshots.
        assertTrue(st.snapshot(id, 40, "Void").hits.isEmpty());
        assertEquals("Void", st.lastDeath(id).cause);
    }
}
