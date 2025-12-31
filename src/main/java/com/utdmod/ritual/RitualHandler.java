package com.utdmod.ritual;

import com.utdmod.core.TensionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

public class RitualHandler {
    
    public static void performRitual(ServerWorld world, PlayerEntity player) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Ritual attempt
            
            // Ritual logic
            double tensionBefore = TensionManager.getTension();
            double tensionReduction = 0.5;
            
            // Apply tension reduction
            TensionManager.reduceTension(tensionReduction);
            
            // Send feedback to player
            player.sendMessage(Text.literal("Ritual performed! Tension reduced by " + tensionReduction));
            
            // Ritual successful
            
            // Performance logged
            
        } catch (Exception e) {
            // Ritual error
            
            // Send error feedback to player
            player.sendMessage(Text.literal("Ritual failed! Error: " + e.getMessage()));
        }
    }
    
    public static void performWardingRitual(ServerWorld world, PlayerEntity player) {
        try {
            double tensionReduction = 0.3;
            TensionManager.reduceTension(tensionReduction);
            
            player.sendMessage(Text.literal("Warding ritual completed! Tension reduced by " + tensionReduction));
            // Warding ritual performed
            
        } catch (Exception e) {
            player.sendMessage(Text.literal("Warding ritual failed: " + e.getMessage()));
            // Warding ritual error
        }
    }
    
    public static void performStormCalmingRitual(ServerWorld world, PlayerEntity player) {
        try {
            // Stronger tension reduction for storm calming
            double tensionReduction = 0.8;
            TensionManager.reduceTension(tensionReduction);
            
            player.sendMessage(Text.literal("Storm calming ritual! Tension reduced by " + tensionReduction));
            // Storm calming ritual performed
            
        } catch (Exception e) {
            player.sendMessage(Text.literal("Storm calming ritual failed: " + e.getMessage()));
            // Storm calming ritual error
        }
    }
    
    public static boolean canPerformRitual(ServerWorld world, PlayerEntity player) {
        // Basic ritual requirements
        return player != null && world != null && !TensionManager.isStormActive();
    }
}
