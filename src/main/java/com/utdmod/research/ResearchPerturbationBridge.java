package com.utdmod.research;

import com.utdmod.UTDMod;
import com.utdmod.experiment.ExperimentLogger;
import com.utdmod.core.TensionTraceClock;
import com.utdmod.tick.TensionLogger;
import com.utdmod.tension.ChunkTensionManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

public final class ResearchPerturbationBridge {

    private ResearchPerturbationBridge() {}

    public static boolean applyLocalPerturbation(
        ServerWorld world,
        ChunkPos chunkPos,
        BlockPos blockPos,
        ServerPlayerEntity player,
        PerturbationType type,
        String detail,
        double amount
    ) {
        if (world == null || chunkPos == null) {
            return false;
        }

        BlockPos normalizedPos = blockPos != null ? blockPos : new BlockPos(chunkPos.getStartX(), 0, chunkPos.getStartZ());
        SubstratePerturbation perturbation = new SubstratePerturbation(
            TensionTraceClock.getServerTick(),
            world,
            chunkPos,
            normalizedPos,
            player,
            type,
            detail,
            amount
        );
        PerturbationRecorder.record(perturbation);
        ExperimentLogger.logPerturbationEvent(world, perturbation);

        if (!ResearchConfig.isEnabled(type)) {
            if (UTDMod.LOGGER.isDebugEnabled()) {
                UTDMod.LOGGER.debug("[RESEARCH] Suppressed {} event: {}", type, perturbation);
            }
            return false;
        }

        double before = ChunkTensionManager.getLocalTension(world, chunkPos);
        if (amount >= 0.0) {
            ChunkTensionManager.addLocalTension(world, chunkPos, amount);
        } else {
            ChunkTensionManager.reduceLocalTension(world, chunkPos, -amount);
        }
        double after = ChunkTensionManager.getLocalTension(world, chunkPos);

        TensionLogger.log(
            TensionTraceClock.getServerTick(),
            type.name(),
            detail != null ? detail : type.name().toLowerCase(),
            amount,
            before,
            after,
            chunkPos,
            player,
            world
        );

        return true;
    }

    public static boolean applyMiningPerturbation(
        ServerWorld world,
        ChunkPos chunkPos,
        BlockPos blockPos,
        ServerPlayerEntity player,
        String blockId
    ) {
        if (world == null || chunkPos == null) {
            return false;
        }

        SubstratePerturbation perturbation = new SubstratePerturbation(
            TensionTraceClock.getServerTick(),
            world,
            chunkPos,
            blockPos != null ? blockPos : new BlockPos(chunkPos.getStartX(), 0, chunkPos.getStartZ()),
            player,
            PerturbationType.MINING,
            blockId,
            0.0
        );
        PerturbationRecorder.record(perturbation);
        ExperimentLogger.logPerturbationEvent(world, perturbation);

        if (!ResearchConfig.isEnabled(PerturbationType.MINING)) {
            if (UTDMod.LOGGER.isDebugEnabled()) {
                UTDMod.LOGGER.debug("[RESEARCH] Suppressed MINING event: {}", perturbation);
            }
            return false;
        }

        ChunkTensionManager.addMiningTension(world, chunkPos, blockId);
        return true;
    }

    public static boolean applyForestryPerturbation(
        ServerWorld world,
        ChunkPos chunkPos,
        BlockPos blockPos,
        ServerPlayerEntity player,
        boolean matureTree
    ) {
        if (world == null || chunkPos == null) {
            return false;
        }

        SubstratePerturbation perturbation = new SubstratePerturbation(
            TensionTraceClock.getServerTick(),
            world,
            chunkPos,
            blockPos != null ? blockPos : new BlockPos(chunkPos.getStartX(), 0, chunkPos.getStartZ()),
            player,
            PerturbationType.FORESTRY,
            matureTree ? "mature_tree" : "sapling_tree",
            matureTree ? 0.0085 : 0.0055
        );
        PerturbationRecorder.record(perturbation);
        ExperimentLogger.logPerturbationEvent(world, perturbation);

        if (!ResearchConfig.isEnabled(PerturbationType.FORESTRY)) {
            if (UTDMod.LOGGER.isDebugEnabled()) {
                UTDMod.LOGGER.debug("[RESEARCH] Suppressed FORESTRY event: {}", perturbation);
            }
            return false;
        }

        ChunkTensionManager.addForestryTension(world, chunkPos, matureTree);
        return true;
    }

    public static boolean applyCombatPerturbation(
        ServerWorld world,
        ChunkPos chunkPos,
        BlockPos blockPos,
        ServerPlayerEntity player,
        String entityType
    ) {
        if (world == null || chunkPos == null) {
            return false;
        }

        SubstratePerturbation perturbation = new SubstratePerturbation(
            TensionTraceClock.getServerTick(),
            world,
            chunkPos,
            blockPos != null ? blockPos : new BlockPos(chunkPos.getStartX(), 0, chunkPos.getStartZ()),
            player,
            PerturbationType.COMBAT,
            entityType,
            0.0
        );
        PerturbationRecorder.record(perturbation);
        ExperimentLogger.logPerturbationEvent(world, perturbation);

        if (!ResearchConfig.isEnabled(PerturbationType.COMBAT)) {
            if (UTDMod.LOGGER.isDebugEnabled()) {
                UTDMod.LOGGER.debug("[RESEARCH] Suppressed COMBAT event: {}", perturbation);
            }
            return false;
        }

        ChunkTensionManager.addSlaughterTension(world, chunkPos, entityType);
        return true;
    }
}
