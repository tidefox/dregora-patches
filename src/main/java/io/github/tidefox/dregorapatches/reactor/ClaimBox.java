package io.github.tidefox.dregorapatches.reactor;

/** The cube around a claimed controller in which frozen-type parts tick. Pure logic. */
public final class ClaimBox {

    private ClaimBox() {}

    public static boolean contains(int cx, int cy, int cz, int radius, int x, int y, int z) {
        return Math.abs(x - cx) <= radius && Math.abs(y - cy) <= radius && Math.abs(z - cz) <= radius;
    }

    /** Chunk coordinate range {minCX, minCZ, maxCX, maxCZ} overlapping the box. */
    public static int[] chunkRange(int cx, int cz, int radius) {
        return new int[] {(cx - radius) >> 4, (cz - radius) >> 4, (cx + radius) >> 4, (cz + radius) >> 4};
    }
}
