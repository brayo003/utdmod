package com.utdmod.tension;

import com.utdmod.core.TensionActivityLedger;
import com.utdmod.tension.ChunkTensionData.ChunkState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

/**
 * Local tension per chunk; primary sink for player activity before it couples to the global field.
 */
public final class ChunkTensionManager {

    private ChunkTensionManager() {}

    public static double getLocalTension(ServerWorld world, ChunkPos pos) {
        ChunkTensionData data = ChunkTensionData.getServerState(world);
        return data.getLocalTension(pos);
    }

    public static ChunkState getChunkState(ServerWorld world, ChunkPos pos) {
        ChunkTensionData data = ChunkTensionData.getServerState(world);
        return data.getChunkState(pos);
    }

    public static SubstrateState getOrCreateSubstrateState(ServerWorld world, ChunkPos pos) {
        ChunkTensionData data = ChunkTensionData.getServerState(world);
        return data.getOrCreateSubstrateState(pos);
    }

    /**
     * Mining contribution injected as a substrate impulse.
     * The impulse is consumed during the next substrate integration tick.
     */
    public static void addMiningTension(ServerWorld world, ChunkPos pos, String blockId) {
        String id = blockId.toLowerCase();
        double baseAmount;
        if (id.contains("ancient_debris")) {
            baseAmount = 0.14;
        } else if (id.contains("diamond_ore") || id.contains("deepslate_diamond")) {
            baseAmount = 0.11;
        } else if (id.contains("emerald_ore") || id.contains("deepslate_emerald")) {
            baseAmount = 0.09;
        } else if (id.contains("deepslate") && id.contains("ore")) {
            baseAmount = 0.078;
        } else if (id.contains("coal_ore") || id.contains("coal_block")) {
            baseAmount = 0.056;
        } else if (id.contains("ore") || id.contains("debris")) {
            baseAmount = 0.052;
        } else if (id.contains("deepslate")) {
            baseAmount = 0.048;
        } else if (id.contains("stone")) {
            baseAmount = 0.042;
        } else {
            baseAmount = 0.008;
        }

        ChunkTensionData data = ChunkTensionData.getServerState(world);
        double currentRate = data.getRecentMiningRate(pos);
        double multiplier = 1.0 + (currentRate * 0.12);
        double applied = baseAmount * multiplier;
        com.utdmod.core.TensionActivityLedger.addMining(applied);
        data.setRecentMiningRate(pos, currentRate + 1.0);
        // Inject as substrate impulse — consumed by integrateAllSubstrateStates
        data.addInstantaneousImpulse(pos, applied);
        if (com.utdmod.UTDMod.LOGGER.isDebugEnabled()) {
            com.utdmod.UTDMod.LOGGER.debug("Mining {} +{} impulse @ {},{}", blockId, applied, pos.x, pos.z);
        }
    }

    public static void addForestryTension(ServerWorld world, ChunkPos pos, boolean matureTree) {
        double baseAmount = matureTree ? 0.0085 : 0.0055;
        ChunkTensionData data = ChunkTensionData.getServerState(world);
        data.addInstantaneousImpulse(pos, baseAmount);
        // Spread weaker impulse to cardinal neighbors
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] d : dirs) {
            data.addInstantaneousImpulse(new ChunkPos(pos.x + d[0], pos.z + d[1]), baseAmount * 0.42);
        }
    }

    public static void addDeforestationTension(ServerWorld world, ChunkPos pos) {
        addForestryTension(world, pos, true);
    }

    /**
     * Combat kills inject local tension at the victim's chunk (target activity scale ~0.015 per typical kill).
     */
    public static void addSlaughterTension(ServerWorld world, ChunkPos pos, String entityType) {
        String id = entityType.toLowerCase();
        double amount = 0.015;
        if (id.contains("villager")) amount = 0.06;
        else if (id.contains("iron_golem")) amount = 0.08;
        else if (id.contains("ender_dragon") || id.contains("wither")) amount = 0.2;

        TensionActivityLedger.addCombat(amount);
        // Inject as substrate impulse
        ChunkTensionData data = ChunkTensionData.getServerState(world);
        data.addInstantaneousImpulse(pos, amount);
    }

    public static int pruneInactive(ServerWorld world, long timeoutMs) {
        ChunkTensionData data = ChunkTensionData.getServerState(world);
        return data.pruneInactive(timeoutMs);
    }
}
