package com.utdmod.storm;

import com.utdmod.signals.TensionManager;
import com.utdmod.ritual.RitualHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

public class StormManager {
    private static final boolean DEBUG_LOGGING = false; // Set to true for development
    
    private static int stormTickCounter = 0;
    private static boolean stormActive = false;
    private static int stormDuration = 0;
    
    // Throttling counters
    private static int heavyEffectCounter = 0;
    private static final int HEAVY_EFFECT_INTERVAL = 10; // ticks (0.5 seconds at 20tps)
    
    public static void tick(ServerWorld world) {
        // DISABLED - Storm system disabled to prevent lag
        return;
    }
    
    private static void triggerStorm(ServerWorld world, double tension, String reason) {
        if (stormActive) {
            if (DEBUG_LOGGING) {
                System.out.printf("[STORM] Storm already active in %s%n", 
                    world.getRegistryKey().getValue());
            }
            return;
        }
        stormActive = true;
        stormDuration = 0;
        if (DEBUG_LOGGING) {
            System.out.printf("[STORM] Storm started in %s (tension: %.2f, reason: %s)%n",
                world.getRegistryKey().getValue(), tension, reason);
        }
        // Trigger storm effects
    }
    
    private static void endStorm(ServerWorld world, double tension, String reason) {
        if (!stormActive) return;
        stormActive = false;
        if (DEBUG_LOGGING) {
            System.out.printf("[STORM] Storm ended in %s after %d ticks (tension: %.2f, reason: %s)%n",
                world.getRegistryKey().getValue(), stormDuration, tension, reason);
        }
        // Clean up storm effects
    }
    
    private static void applyLightEffects(ServerWorld world, double tension) {
        // Lightweight storm effects
    }
    
    private static void applyHeavyEffects(ServerWorld world, double tension) {
        // Heavy storm effects (throttled)
    }
    
    public static boolean isStormActive() {
        return stormActive;
    }
    
    public static int getStormDuration() {
        return stormDuration;
    }
    
    public static void triggerRitualStorm(ServerWorld world, PlayerEntity player) {
        if (RitualHandler.canPerformRitual(world, player)) {
            triggerStorm(world, 1.5, "RITUAL_TRIGGER");
            player.sendMessage(Text.literal("Storm ritual activated!"));
        }
    }
    
    public static void calmStorm(ServerWorld world, double calmAmount) {
        if (stormActive) {
            double currentTension = TensionManager.getTension();
            TensionManager.reduceTension(calmAmount);
            
            if (TensionManager.getTension() < 0.5) {
                endStorm(world, TensionManager.getTension(), "RITUAL_CALMING");
            }
        }
    }
}
