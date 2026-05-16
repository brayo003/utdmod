package com.utdmod.client;

/**
 * Client-side counters for TEST4 reporting (sparse server upload).
 */
public final class ClientFeelingCounters {

    public static volatile int audioTriggers;
    public static volatile double overlayIntensity;
    public static volatile int hostileNearLast;

    private ClientFeelingCounters() {}

    public static void resetForReport() {
        audioTriggers = 0;
    }
}
