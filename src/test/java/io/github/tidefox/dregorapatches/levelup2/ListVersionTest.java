package io.github.tidefox.dregorapatches.levelup2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class ListVersionTest {

    @Test
    public void changesOnStructuralChangesOnly() {
        List<String> list = new ArrayList<String>(Arrays.asList("a", "b", "c"));
        long v0 = ListVersion.of(list);
        assertNotEquals(ListVersion.UNKNOWN, v0);
        list.get(1);
        assertEquals(v0, ListVersion.of(list));

        list.add("d");
        long v1 = ListVersion.of(list);
        assertNotEquals(v0, v1);
        list.remove("a");
        long v2 = ListVersion.of(list);
        assertNotEquals(v1, v2);
        list.removeAll(Arrays.asList("b"));
        assertNotEquals(v2, ListVersion.of(list));
    }

    @Test
    public void otherListTypesAreUnknown() {
        assertEquals(ListVersion.UNKNOWN, ListVersion.of(new LinkedList<String>()));
        assertEquals(ListVersion.UNKNOWN, ListVersion.of(null));
    }
}
