package com.utdmod.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

/**
 * Unified TensionManager - single authoritative source
 */
public class TensionManager {
    private static double globalTension = 0.0;
    private static boolean stormActive = false;
    private static final double T_MAX = 2.0;
    private static final double LAMBDA = 0.001;
    private static final double ALPHA = 0.002;
    private static final double STORM_THRESHOLD = 1.0;
    private static final double STORM_HYSTERESIS = 0.7;
    private static double eventEnergy = 0.0;
    private static double continuousInflow = 0.0;
    // Corruption thresholds and effects
    private static final double[] CORRUPTION_THRESHOLDS = {0.8, 1.5, 2.5, 4.0};
    private static final String[] CORRUPTION_LEVELS = {"Subtle", "Moderate", "Severe", "Catastrophic"};
    private static int currentCorruptionLevel = 0;
    
    // Getters
    public static double getTension() {
        return globalTension;
    }
    
    public static boolean isStormActive() {
        return stormActive;
    }
    
    // Setters with validation
    public static void setTension(double tension) {
        globalTension = Math.max(0.0, Math.min(T_MAX, tension)); // Clamp between 0 and T_MAX
        updateCorruptionState();
    }
    
    // Add continuous inflow (e.g., walking)
    public static void setContinuousInflow(double inflow) {
        continuousInflow = inflow;
    }
    
    // Add event-based tension (buffered impulses)
    public static void addEvent(double amount) {
        eventEnergy += amount;
        System.out.printf("[UTDMod-Signal] Event added %.3f to buffer%n", amount);
    }
    
    // Mining pressure - buffered event
    public static void addMiningTension(String blockId) {
        addEvent(0.0015);
    }
    
    // Deforestation - local only, no global event
    public static void addDeforestationTension() {
        // Local only
    }
    
    // Mob slaughter - buffered event
    public static void addSlaughterTension(String entityType) {
        addEvent(0.003);
    }
    
    // Full tension update with nonlinear dynamics
    public static void tickTension() {
        // 1. Gather excitation
        double inflow = continuousInflow + eventEnergy;
        
        // 2. Apply linear decay
        globalTension -= LAMBDA * globalTension;
        
        // 3. Apply nonlinear amplification
        globalTension += ALPHA * globalTension * globalTension;
        
        // 4. Add inflow
        globalTension += inflow;
        
        // 5. Clamp
        globalTension = Math.max(0.0, Math.min(T_MAX, globalTension));
        
        // 6. Update storm with hysteresis
        if (globalTension > STORM_THRESHOLD && !stormActive) {
            stormActive = true;
        }
        if (globalTension < STORM_HYSTERESIS) {
            stormActive = false;
        }
        
        // 7. Update corruption state
        updateCorruptionState();
        
        // 8. Decay event energy
        eventEnergy *= 0.9;
        
        // 9. Print T for calibration
        System.out.printf("[UTDMod-Tension] Global T=%.4f, inflow=%.6f, eventEnergy=%.6f, storm=%b%n", globalTension, inflow, eventEnergy, stormActive);
    }
    
    public static int getCorruptionLevel() {
        return currentCorruptionLevel;
    }
    
    public static String getCorruptionLevelName() {
        return currentCorruptionLevel > 0 ? CORRUPTION_LEVELS[Math.min(currentCorruptionLevel - 1, CORRUPTION_LEVELS.length - 1)] : "None";
    }
    
    private static void updateCorruptionState() {
        int newLevel = 0;
        for (int i = CORRUPTION_THRESHOLDS.length - 1; i >= 0; i--) {
            if (globalTension >= CORRUPTION_THRESHOLDS[i]) {
                newLevel = i + 1;
                break;
            }
        }
        
        if (newLevel != currentCorruptionLevel) {
            currentCorruptionLevel = newLevel;
            // Could add corruption level change events here
        }
        
        // Storm handled separately in tickTension
    }
    
    public static void reduceTension(double amount) {
        globalTension = Math.max(0.0, globalTension - amount);
        updateCorruptionState();
    }
    
    // Event triggers (no logging)
    public static void triggerWraithEvent(World world) {
        // Spawn wraith logic here
    }
    
    public static void triggerSerpentEvent(World world) {
        // Spawn serpent logic here
    }
    
    // Legacy compatibility
    public static TensionManager getServerState(MinecraftServer server) {
        return new TensionManager(); // Singleton pattern
    }
    
    public float calculateBaseTension(ServerPlayerEntity player) {
        float T_base = 0.005f;
        
        if (player.getServer() != null) {
            // Dynamic difficulty multiplier
            float M_dynamic = 1.0f;
            T_base *= M_dynamic;
        }
        
        return T_base;
    }
}
