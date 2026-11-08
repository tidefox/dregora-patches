package io.github.tidefox.dregorapatches.reactor;

/** Coordinate display for /reactor (docs/07 §3 "Coordinates"). Pure logic. */
public final class CoordFormat {

    private CoordFormat() {}

    /** Rounds to the nearest multiple of {@code step} (half away from zero). */
    public static int estimate(int v, int step) {
        if (step <= 1) return v;
        long half = step / 2;
        long a = Math.abs((long) v);
        long r = ((a + half) / step) * step;
        return (int) (v < 0 ? -r : r);
    }

    public static String estimated(int x, int z, int step) {
        return "≈ " + estimate(x, step) + ", " + estimate(z, step);
    }

    public static String exact(int x, int y, int z) {
        return x + " " + y + " " + z;
    }
}
