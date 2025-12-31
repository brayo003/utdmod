package com.utdmod.signals;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

public class TensionManager {
    private static double globalTension = 0.0;
    private static boolean stormActive = false;
    
    public static double getTension() {
        return globalTension;
    }
    
    public static void setTension(double tension) {
        globalTension = Math.max(0.0, tension);
    }
    
    public static void increaseTension(ServerPlayerEntity player, double amount) {
        globalTension += amount;
        if (globalTension > 1.0) {
            stormActive = true;
        }
    }
    
    public static void reduceTension(double amount) {
        globalTension = Math.max(0.0, globalTension - amount);
        if (globalTension < 0.5) {
            stormActive = false;
        }
    }
    
    public static boolean isStormActive() {
        return stormActive;
    }
    
    public static void triggerRitualEffect(ServerPlayerEntity player) {
        reduceTension(0.5);
    }
    
    public static void triggerWraithEvent(World world) {
        // Stub implementation - removed logging to prevent lag
    }
    
    public static void triggerSerpentEvent(World world) {
        // Stub implementation - removed logging to prevent lag
    }
    
    public float calculateBaseTension(ServerPlayerEntity player) {
        float T_base = 0.005f;
        
        if (player.getServer() != null) {
            // Stub for DynamicDifficultyScaler
            float M_dynamic = 1.0f;
            // Removed logging to prevent lag
            T_base *= M_dynamic;
        }
        
        return T_base;
    }
    
    public static TensionManager getServerState(MinecraftServer server) {
        // Stub implementation - returns singleton
        return new TensionManager();
    }
    
    public double getTension(ServerPlayerEntity player) {
        return globalTension;
    }
    
    public void reduceTension(ServerPlayerEntity player, float amount) {
        reduceTension(amount);
    }
}
