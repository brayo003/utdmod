package com.utdmod.util;

public class RandomNoise {
    private double value = 0;

    public double next() {
        double raw = Math.random() * 2 - 1;
        value = 0.95 * value + 0.05 * raw;
        return value;
    }
}
