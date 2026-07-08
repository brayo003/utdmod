package com.utdmod.research.experiment;

import com.utdmod.config.UTDConfig;
import com.utdmod.research.PerturbationType;
import com.utdmod.research.ResearchConfig;
import com.utdmod.research.player.ResearchPlayerContext;
import com.utdmod.research.player.ResearchPlayerEntityFactory;
import com.utdmod.research.player.ResearchPlayerPersistence;
import com.utdmod.research.player.ResearchPlayerRegistry;
import com.utdmod.research.player.ResearchPlayerLogger;
import com.utdmod.research.player.ResearchPlayerSnapshot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Central orchestration layer for experiment injection.
 * Schedules and assigns deterministic protocols to Batimore agents.
 *
 * It does not implement gameplay logic; gameplay happens in the player executor
 * via injected {@link ResearchExperiment} protocols.
 */
public final class ResearchExperimentManager {

  private ResearchExperimentManager() {}

  public static void applyFieldEnabled(boolean enabled) {
    for (PerturbationType type : PerturbationType.values()) {
      ResearchConfig.setEnabled(type, enabled);
    }
  }

  public static void applyCouplingPreset(String presetId) {
    String p = presetId == null ? "LAB" : presetId.trim().toUpperCase();
    switch (p) {
      case "LAB" -> UTDConfig.substrateGameplayTuningEnabled = false;
      case "TUNED" -> UTDConfig.substrateGameplayTuningEnabled = true;
      default -> UTDConfig.substrateGameplayTuningEnabled = false;
    }
  }

  public static void spawnAgent(ServerWorld world, String agentId, ResearchExperimentConfig cfg) {
    if (cfg == null) return;
    if (agentId == null || agentId.isBlank()) agentId = "agent0";

    applyFieldEnabled(cfg.fieldEnabled);
    applyCouplingPreset(cfg.couplingPresetId);

    BlockPos start = cfg.startingPos != null ? cfg.startingPos : ResearchPlayerEntityFactory.defaultSpawn(world, cfg.botSeed);
    ServerPlayerEntity player = ResearchPlayerEntityFactory.spawn(world, agentId, cfg.botSeed, start);
    if (player == null) return;

    ResearchPlayerPersistence persistence = ResearchPlayerPersistence.get(world, player.getUuid());
    ResearchPlayerSnapshot snapshot = persistence.snapshot();
    snapshot.agentId = agentId;
    snapshot.botSeed = cfg.botSeed;
    snapshot.experimentId = cfg.experimentId;
    snapshot.protocolId = cfg.protocolId;
    snapshot.previousProtocolId = cfg.protocolId;
    snapshot.loggingLabel = cfg.loggingLabel;
    snapshot.allowedRadiusChunks = cfg.allowedRadiusChunks;
    snapshot.durationTicks = cfg.durationTicks;
    snapshot.experimentSeed = cfg.experimentSeed;

    snapshot.miningOreId = cfg.miningOreId;
    snapshot.miningGoalCount = cfg.miningGoalCount;

    snapshot.stateTicks = 0;
    snapshot.running = true;
    snapshot.paused = false;
    snapshot.spawned = true;
    snapshot.homePos = start.toImmutable();
    snapshot.transitionReason = "experiment spawn";
    persistence.saveSnapshot(snapshot);

    // Ensure bot executor runs.
    ResearchConfig.setBotEnabled(true);

    // Ensure protocol observability logging is enabled.
    ResearchPlayerLogger.initialize(cfg.botSeed);
    ResearchPlayerLogger.setEnabled(true);
  }

  public static void assignExperiment(ServerWorld world, String agentId, ResearchExperimentConfig cfg) {
    if (cfg == null) return;
    if (agentId == null || agentId.isBlank()) agentId = "agent0";

    applyFieldEnabled(cfg.fieldEnabled);
    applyCouplingPreset(cfg.couplingPresetId);

    ServerPlayerEntity player = findAgent(world, agentId);
    if (player == null) {
      spawnAgent(world, agentId, cfg);
      return;
    }

    ResearchPlayerPersistence persistence = ResearchPlayerPersistence.get(world, player.getUuid());
    ResearchPlayerSnapshot snapshot = persistence.snapshot();
    snapshot.agentId = agentId;
    snapshot.experimentId = cfg.experimentId;
    snapshot.protocolId = cfg.protocolId;
    snapshot.previousProtocolId = snapshot.protocolId;
    snapshot.loggingLabel = cfg.loggingLabel;
    snapshot.allowedRadiusChunks = cfg.allowedRadiusChunks;
    snapshot.durationTicks = cfg.durationTicks;
    snapshot.experimentSeed = cfg.experimentSeed;
    snapshot.miningOreId = cfg.miningOreId;
    snapshot.miningGoalCount = cfg.miningGoalCount;

    snapshot.stateTicks = 0;
    snapshot.running = true;
    snapshot.paused = false;
    snapshot.spawned = true;
    snapshot.transitionReason = "protocol assigned";
    persistence.saveSnapshot(snapshot);

    ResearchConfig.setBotEnabled(true);

    ResearchPlayerLogger.initialize(cfg.botSeed);
    ResearchPlayerLogger.setEnabled(true);
  }

  public static void stopAgent(ServerWorld world, String agentId) {
    ServerPlayerEntity player = findAgent(world, agentId);
    if (player == null) return;
    ResearchPlayerPersistence persistence = ResearchPlayerPersistence.get(world, player.getUuid());
    ResearchPlayerSnapshot snapshot = persistence.snapshot();
    snapshot.running = false;
    snapshot.paused = false;
    persistence.saveSnapshot(snapshot);

    if (!anyAgentRunning(world)) {
      ResearchConfig.setBotEnabled(false);
    }
  }

  private static boolean anyAgentRunning(ServerWorld world) {
    for (ServerPlayerEntity p : ResearchPlayerRegistry.getAll()) {
      if (p.getServerWorld() != world || !p.isAlive()) continue;
      ResearchPlayerSnapshot s = ResearchPlayerPersistence.get(world, p.getUuid()).snapshot();
      if (s.running && !s.paused) return true;
    }
    return false;
  }

  private static ServerPlayerEntity findAgent(ServerWorld world, String agentId) {
    for (ServerPlayerEntity p : ResearchPlayerRegistry.getAll()) {
      if (p.getServerWorld() != world || !p.isAlive()) continue;
      if (agentId.equals(ResearchPlayerRegistry.getAgentId(p))) {
        return p;
      }
    }
    return null;
  }

  public static ResearchPlayerSnapshot status(ServerWorld world, String agentId) {
    ServerPlayerEntity player = findAgent(world, agentId);
    if (player == null) {
      return null;
    }
    return ResearchPlayerPersistence.get(world, player.getUuid()).snapshot();
  }
}

