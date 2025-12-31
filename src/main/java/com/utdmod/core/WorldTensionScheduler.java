package com.utdmod.core;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.List;

/**
 * Single world scheduler - runs once per second
 * Replaces all per-entity tick mixins
 */
public class WorldTensionScheduler {
    private static int tickCounter = 0;
    private static double lastTension = 0.0;
    
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            
            // Run once per second (20 ticks)
            if (tickCounter % 20 == 0) {
                runSecondlyUpdates(server);
                
                // Single heartbeat log every 10 seconds
                if (tickCounter % 200 == 0) {
                    System.out.println("[UTD] Heartbeat: Tension=" + String.format("%.2f", TensionManager.getTension()));
                }
            }
        });
    }
    
    private static void runSecondlyUpdates(MinecraftServer server) {
        double currentTension = TensionManager.getTension();
        
        // Only process if tension actually changed
        if (Math.abs(currentTension - lastTension) > 0.01) {
            lastTension = currentTension;
            
            // Event-based effects only when tension changes
            onTensionChange(currentTension, server);
        }
    }
    
    private static void onTensionChange(double tension, MinecraftServer server) {
        // Reactive effects based on tension thresholds
        if (tension > 1.0 && lastTension <= 1.0) {
            // Storm start
            triggerStormStart(server);
        } else if (tension <= 1.0 && lastTension > 1.0) {
            // Storm stop
            triggerStormStop(server);
        }
        
        // Mob behavior changes
        if (tension > 0.7) {
            triggerHighTensionEffects(server);
        }
        
        // Ritual activation check
        checkRitualActivations(server);
    }
    
    private static void triggerStormStart(MinecraftServer server) {
        // Storm start logic
        for (ServerWorld world : server.getWorlds()) {
            if (world.isRaining()) continue;
            // Set storm state
            world.setWeather(6000, 0, true, false); // 5 minutes of rain
        }
    }
    
    private static void triggerStormStop(MinecraftServer server) {
        // Storm stop logic
        for (ServerWorld world : server.getWorlds()) {
            if (!world.isRaining()) continue;
            world.setWeather(0, 0, false, false); // Clear weather
        }
    }
    
    private static void triggerHighTensionEffects(MinecraftServer server) {
        // High tension effects - batch processing
        for (ServerWorld world : server.getWorlds()) {
            List<ServerPlayerEntity> players = world.getPlayers();
            for (ServerPlayerEntity player : players) {
                // Apply effects to players in range
                if (world.random.nextFloat() < 0.1f) {
                    // Random effect
                    player.addExperience(5); // Small experience bonus
                }
            }
        }
    }
    
    private static void checkRitualActivations(MinecraftServer server) {
        // Check for ritual conditions
        for (ServerWorld world : server.getWorlds()) {
            List<ServerPlayerEntity> players = world.getPlayers();
            for (ServerPlayerEntity player : players) {
                // Simple ritual check - if player has specific items and tension is right
                if (player.getMainHandStack().getItem().toString().contains("crystal") && 
                    TensionManager.getTension() > 0.5) {
                    // Trigger ritual effect
                    TensionManager.setTension(Math.max(0, TensionManager.getTension() - 0.2));
                    break; // Only one ritual per second
                }
            }
        }
    }
}
