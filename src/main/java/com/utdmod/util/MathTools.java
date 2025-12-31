package com.utdmod.util;

public class MathTools {
    public static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
