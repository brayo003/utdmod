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
    private static final double TENSION_DECAY_RATE = 0.005;
    
    // Getters
    public static double getTension() {
        return globalTension;
    }
    
    public static boolean isStormActive() {
        return stormActive;
    }
    
    // Setters with validation
    public static void setTension(double tension) {
        globalTension = Math.max(0.0, Math.min(2.0, tension)); // Clamp between 0 and 2
        updateStormState();
    }
    
    public static void increaseTension(ServerPlayerEntity player, double amount) {
        globalTension += amount;
        if (globalTension > 2.0) globalTension = 2.0;
        updateStormState();
    }
    
    public static void reduceTension(double amount) {
        globalTension = Math.max(0.0, globalTension - amount);
        updateStormState();
    }
    
    // Decay (called every tick)
    public static void tickDecay() {
        globalTension = Math.max(0.0, globalTension - TENSION_DECAY_RATE);
        updateStormState();
    }
    
    private static void updateStormState() {
        stormActive = globalTension > 1.0;
    }
    
    // Ritual effects
    public static void triggerRitualEffect(ServerPlayerEntity player) {
        reduceTension(0.5);
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
