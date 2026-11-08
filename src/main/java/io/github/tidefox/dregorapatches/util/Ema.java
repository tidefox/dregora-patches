package io.github.tidefox.dregorapatches.util;

/** Exponential moving average (alpha 1/100 ≈ the last 5 s of ticks). */
public final class Ema {

    private final double alpha;
    private double value;
    private boolean seeded;

    public Ema(double alpha) {
        this.alpha = alpha;
    }

    public void add(double sample) {
        if (!seeded) {
            value = sample;
            seeded = true;
        } else {
            value += alpha * (sample - value);
        }
    }

    public double get() {
        return value;
    }
}
