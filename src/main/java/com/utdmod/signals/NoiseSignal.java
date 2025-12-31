package com.utdmod.signals;

import com.utdmod.util.RandomNoise;

public class NoiseSignal {

    private RandomNoise noise = new RandomNoise();

    public double compute() {
        return noise.next();
    }
}
