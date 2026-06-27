package com.utdmod.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Client-side mirror of server tension, updated only from {@link com.utdmod.network.TensionSyncPacket}.
 * UI and audio should read this — never {@link com.utdmod.core.TensionManager} on the client.
 */
@Environment(EnvType.CLIENT)
public final class TensionSyncState {

    public static volatile double CLIENT_TENSION = 0.0;
    /** Local chunk tension at the player's position (server-synced). */
    public static volatile double CLIENT_LOCAL_TENSION = 0.0;
    /** 8×8 region aggregates (may lag up to 200t between server refreshes). */
    public static volatile double CLIENT_REGION_AVG = 0.0;
    public static volatile double CLIENT_REGION_MAX = 0.0;
    public static volatile boolean CLIENT_STORM = false;
    // Per-current-region metadata (0 means none)
    public static volatile int CLIENT_REGION_ID = 0;
    public static volatile int CLIENT_REGION_AGE = 0;
    public static volatile double CLIENT_REGION_MATURITY = 0.0;
    public static volatile int CLIENT_REGION_CHUNK_COUNT = 0;
    public static volatile int CLIENT_REGION_STATE = 0; // ordinal of com.utdmod.core.RegionState

    /** Exponential smoothing targets for experiential feedback (audio / overlay). */
    private static double smoothedGlobal = 0.0;
    private static double smoothedLocal = 0.0;

    private TensionSyncState() {}

    /**
     * Combined signal for feedback: local buildup matters as much as the global field.
     * Uses smoothed values so audio/visual ramp without stair-stepping on 20-tick sync.
     */
    public static double perceivedTension() {
        return Math.max(smoothedGlobal, smoothedLocal * 0.92);
    }

    /**
     * Regional feel proxy: max of synced regional max and perceived local/global blend.
     */
    public static double perceivedRegionalFeel() {
        return Math.max(CLIENT_REGION_MAX, perceivedTension());
    }

    /**
     * Advance smoothing toward latest server snapshot. Call once per client tick.
     */
    public static void tickSmoothing() {
        double g = CLIENT_TENSION;
        double l = CLIENT_LOCAL_TENSION;
        double aG = 0.07;
        double aL = 0.11;
        smoothedGlobal += (g - smoothedGlobal) * aG;
        smoothedLocal += (l - smoothedLocal) * aL;
    }

    public static void applySnapshot(double tension, boolean storm, double localTension, double regionAvg, double regionMax) {
        CLIENT_TENSION = tension;
        CLIENT_STORM = storm;
        CLIENT_LOCAL_TENSION = localTension;
        CLIENT_REGION_AVG = regionAvg;
        CLIENT_REGION_MAX = regionMax;
    }

    public static void applySnapshot(double tension, boolean storm, double localTension, double regionAvg, double regionMax,
                                     int regionId, int regionAge, double regionMaturity, int regionChunkCount, int regionStateOrdinal) {
        applySnapshot(tension, storm, localTension, regionAvg, regionMax);
        CLIENT_REGION_ID = regionId;
        CLIENT_REGION_AGE = regionAge;
        CLIENT_REGION_MATURITY = regionMaturity;
        CLIENT_REGION_CHUNK_COUNT = regionChunkCount;
        CLIENT_REGION_STATE = regionStateOrdinal;
    }

    public static void applySnapshot(double tension, boolean storm, double localTension) {
        applySnapshot(tension, storm, localTension, localTension, localTension);
    }

    /** Legacy callers without local chunk data. */
    public static void applySnapshot(double tension, boolean storm) {
        applySnapshot(tension, storm, 0.0);
    }
}
