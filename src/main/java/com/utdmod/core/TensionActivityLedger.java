package com.utdmod.core;

/**
 * Per-server-tick accumulation of local activity magnitudes for [GLOBAL_FLOW] attribution
 * (local activity does not enter global directly; it explains coupling context the same tick).
 */
public final class TensionActivityLedger {

    private static double movement;
    private static double mining;
    private static double combat;
    private static double ritual;

    private TensionActivityLedger() {}

    public static void reset() {
        movement = 0.0;
        mining = 0.0;
        combat = 0.0;
        ritual = 0.0;
    }

    public static void addMovement(double amount) {
        movement += amount;
    }

    public static void addMining(double amount) {
        mining += amount;
    }

    public static void addCombat(double amount) {
        combat += amount;
    }

    public static void addRitual(double amount) {
        ritual += amount;
    }

    public static double getMovement() {
        return movement;
    }

    public static double getMining() {
        return mining;
    }

    public static double getCombat() {
        return combat;
    }

    public static double getRitual() {
        return ritual;
    }
}
