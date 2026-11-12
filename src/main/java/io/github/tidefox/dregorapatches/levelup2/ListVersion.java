package io.github.tidefox.dregorapatches.levelup2;

import java.lang.reflect.Field;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * Structural-change counter of an {@link ArrayList} ({@code AbstractList.modCount}): changes on every
 * add/remove/clear, so an unchanged value means the list holds the same elements in the same order.
 * {@link #UNKNOWN} for any other list type (another mod swapped the list) or if reflection fails.
 * Pure logic so it can be unit tested.
 */
public final class ListVersion {

    public static final long UNKNOWN = Long.MIN_VALUE;

    private static final Field MOD_COUNT = find();

    private ListVersion() {}

    public static long of(List<?> list) {
        if (MOD_COUNT == null || list == null || list.getClass() != ArrayList.class) return UNKNOWN;
        try {
            return MOD_COUNT.getInt(list);
        } catch (IllegalAccessException e) {
            return UNKNOWN;
        }
    }

    private static Field find() {
        try {
            Field f = AbstractList.class.getDeclaredField("modCount");
            f.setAccessible(true);
            return f;
        } catch (Exception e) {
            return null;
        }
    }
}
