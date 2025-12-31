package com.utdmod.net;

public final class SmallSignalClientProxy {
    private static volatile double clientTension = 0.0;
    public static void setClientTension(double t) { clientTension = t; }
    public static double getClientTension() { return clientTension; }
}
