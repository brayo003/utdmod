package com.utdmod.tension;

import com.utdmod.core.TensionManager;
import com.utdmod.tension.ChunkTensionData.ChunkState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

/**
 * Manages local tension per chunk, integrating with global tension system.
 */
public class ChunkTensionManager {
    /**
     * Get local tension for a specific chunk.
     */
    public static double getLocalTension(ServerWorld world, ChunkPos pos) {
        ChunkTensionData data = ChunkTensionData.getServerState(world);
        return data.getLocalTension(pos);
    }

    /**
     * Get chunk state for a specific chunk.
     */
    public static ChunkState getChunkState(ServerWorld world, ChunkPos pos) {
        ChunkTensionData data = ChunkTensionData.getServerState(world);
        return data.getChunkState(pos);
    }

    /**
     * Set local tension for a specific chunk.
     */
    public static void setLocalTension(ServerWorld world, ChunkPos pos, double tension) {
        ChunkTensionData data = ChunkTensionData.getServerState(world);
        data.setLocalTension(pos, tension, world);
    }

    /**
     * Add tension to a specific chunk (local).
     */
    public static void addLocalTension(ServerWorld world, ChunkPos pos, double amount) {
        ChunkTensionData data = ChunkTensionData.getServerState(world);
        data.addLocalTension(pos, amount, world);
    }

    /**
     * Add mining tension to a chunk (local).
     */
    public static void addMiningTension(ServerWorld world, ChunkPos pos, String blockId) {
        double baseAmount = 0.0;
        if (blockId.contains("stone")) baseAmount = 0.01;
        else if (blockId.contains("ore") || blockId.contains("coal") || blockId.contains("iron") || blockId.contains("gold")) baseAmount = 0.05;
        else if (blockId.contains("diamond") || blockId.contains("emerald") || blockId.contains("ancient_debris")) baseAmount = 0.3;
        else baseAmount = 0.005; // Default for other blocks

        ChunkTensionData data = ChunkTensionData.getServerState(world);
        data.addMiningTension(pos, baseAmount, world);
        System.out.printf("[UTDMod-Signal] Mining block %s added %.3f local tension to chunk %d,%d%n", blockId, baseAmount, pos.x, pos.z);
    }

    /**
     * Add deforestation tension to a chunk (local).
     */
    public static void addDeforestationTension(ServerWorld world, ChunkPos pos) {
        addLocalTension(world, pos, 0.02);
        System.out.printf("[UTDMod-Signal] Tree destruction added 0.020 local tension to chunk %d,%d%n", pos.x, pos.z);
    }

    /**
     * Add slaughter tension (global, as per design).
     */
    public static void addSlaughterTension(String entityType) {
        double amount = 0.03; // Default for passive mobs
        if (entityType.contains("villager")) amount = 0.2;
        else if (entityType.contains("iron_golem")) amount = 0.4;
        else if (entityType.contains("ender_dragon") || entityType.contains("wither")) amount = 1.0;

        TensionManager.addEvent(amount);
        System.out.printf("[UTDMod-Signal] Killed %s added %.3f global tension%n", entityType, amount);
    }

    /**
     * Reduce local tension in a chunk.
     */
    public static void reduceLocalTension(ServerWorld world, ChunkPos pos, double amount) {
        double current = getLocalTension(world, pos);
        setLocalTension(world, pos, Math.max(0, current - amount));
    }
}
