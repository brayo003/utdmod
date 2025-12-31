package com.utdmod.storm;

public class TensionManager {
private static double globalTension = 0.0;

public static double getGlobalTension() {
    return globalTension;
}

public static void reduceTension(double amount) {
    globalTension = Math.max(0.0, globalTension - amount);
}

public static void increaseTension(double amount) {
    globalTension += amount;
}


}
