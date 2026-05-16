package com.utdmod.core;

/**
 * Server tick counter mirror for logging from {@link TensionManager} without depending on {@link TensionServerTick}.
 */
public final class TensionTraceClock {

    private static volatile long serverTick = 0L;

    private TensionTraceClock() {}

    public static void setServerTick(long tick) {
        serverTick = tick;
    }

    public static long getServerTick() {
        return serverTick;
    }
}
