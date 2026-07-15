package com.utdmod.util;

import java.util.Random;

/**
 * Smoothed noise signal using an exponential moving average over a seeded PRNG.
 *
 * <p>Uses an instance {@link Random} rather than {@code Math.random()} so that
 * callers can supply a seed and obtain reproducible output. The class is currently
 * unreachable from the substrate integration hot path (A/M/D/B tick loop).
 * If it is wired into any path that feeds {@code externalInput} or
 * {@code incomingTransport}, the caller must pass a deterministic seed derived
 * from the world seed and tick counter, not a wall-clock value.
 */
public class RandomNoise {
    private final Random rng;
    private double value = 0;

    /** Creates a noise source with an unseeded Random (non-reproducible). */
    public RandomNoise() {
        this.rng = new Random();
    }

    /** Creates a noise source with a fixed seed (reproducible). */
    public RandomNoise(long seed) {
        this.rng = new Random(seed);
    }

    public double next() {
        double raw = rng.nextDouble() * 2 - 1;
        value = 0.95 * value + 0.05 * raw;
        return value;
    }
}
