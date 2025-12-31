package com.utdmod.tension;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TensionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("UTD_Tension");
    private static double globalTension = 0.0;

    public static double getGlobalTension() {
        return globalTension;
    }

    public static void reduceTension(double amount) {
        double oldTension = globalTension;
        globalTension = Math.max(0, globalTension - amount);
        LOGGER.info("TENSION_REDUCED: {} -> {} (amount: {})", oldTension, globalTension, amount);
    }

    public static void addTension(double amount) {
        double oldTension = globalTension;
        globalTension += amount;
        LOGGER.info("TENSION_ADDED: {} -> {} (amount: {})", oldTension, globalTension, amount);
        
        // Log tension threshold triggers
        if (globalTension >= 100.0 && oldTension < 100.0) {
            LOGGER.warn("TENSION_THRESHOLD_HIGH: Tension exceeded 100.0 - Storm conditions imminent");
        } else if (globalTension >= 50.0 && oldTension < 50.0) {
            LOGGER.warn("TENSION_THRESHOLD_MEDIUM: Tension exceeded 50.0 - Increased activity detected");
        }
    }
}
