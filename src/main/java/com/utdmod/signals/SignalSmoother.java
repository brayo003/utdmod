package com.utdmod.signals;

public class SignalSmoother {

    private double value = 0;

    public double push(double in) {
        value = (value * 0.8) + (in * 0.2);
        return value;
    }
}
