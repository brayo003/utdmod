package com.utdmod;

public class UTDProfiler {

    private static long startTime = 0;
    private static int logCounter = 0;
    private static final int LOG_INTERVAL = 200; // Log every 200 calls (~10 seconds at 20Hz)

    public static void start(String name) {
        startTime = System.nanoTime();
    }

    public static void end(String name) {
        long elapsed = System.nanoTime() - startTime;
        double elapsedMs = elapsed / 1_000_000.0;
        logCounter++;
        if (logCounter >= LOG_INTERVAL) {
            UTDMod.LOGGER.info("[UTD-Profiler] {} took {} ms", name, String.format("%.2f", elapsedMs));
            logCounter = 0;
        }
    }
}
