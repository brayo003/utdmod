package com.utdmod.storm;

public class TensionManager {
    // V12 Engine Constants
    private static double globalTension = 0.0;
    private static String phase = "NOMINAL";
    private static double gammaMax = 0.8;
    private static double gamma = 0.8;
    private static final double BETA = 3.5;
    private static final double DT = 0.05;
    private static final double DECAY_RATE = 0.0005;

    public static double getGlobalTension() {
        return globalTension;
    }

    public static String getPhase() {
        return phase;
    }

    // V12 Excitation Flux Implementation
    private static double calculateExcitation(double signal) {
        if (signal < 45.0) {
            return 1.0 - Math.exp(-signal / 40.0);
        }
        return 0.675 + ((signal - 45.0) / 20.0);
    }

    // The Core V12 Step Function
    public static void updateTension(double signal) {
        // Apply temporal decay to damping ability
        gamma *= (1.0 - DECAY_RATE);
        
        double E = calculateExcitation(signal);
        double gammaEff = phase.equals("FIREWALL") ? 2.2 : 1.0;
        
        double inflow = E * BETA;
        double outflow = gammaEff * gamma * globalTension;
        
        // Transport Equation integration
        globalTension += (inflow - outflow) * DT;
        
        // Regime Shift Logic
        if (globalTension > 1.0) {
            phase = "FIREWALL";
        } else if (phase.equals("FIREWALL") && globalTension < 0.4) {
            phase = "NOMINAL";
        }
    }

    public static void applyIntervention(boolean isDeep) {
        if (isDeep) {
            gamma = Math.min(gammaMax, gamma * 1.15);
            globalTension *= 0.60; // 40% Flush
        } else {
            gamma = Math.min(gammaMax, gamma * 1.05);
        }
    }
}
