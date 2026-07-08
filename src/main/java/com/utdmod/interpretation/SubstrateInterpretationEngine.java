package com.utdmod.interpretation;

import com.utdmod.tension.SpatialStencil;
import com.utdmod.tension.SubstrateState;
import net.minecraft.util.math.ChunkPos;

import java.util.Map;

/**
 * Shared read-only interpretation engine.
 * Transforms substrate variables and neighborhood context into semantic descriptions and intents.
 * Does not modify substrate state or duplicate field derivative mathematics.
 */
public final class SubstrateInterpretationEngine {
  private SubstrateInterpretationEngine() {}

  public static SubstrateSemantics computeSemantics(ChunkPos pos, SubstrateState center,
                                                    Map<ChunkPos, SubstrateState> snapshot) {
    double activation = center.getActivation();
    double memory = center.getMemory();
    double transport = center.getTransport();
    double fieldDepth = center.getFieldDepth();
    double baseline = center.getBaseline();
    double readiness = center.getReadiness();
    double capacity = center.getCapacity();
    double convergence = center.getConvergence();

    SpatialStencil stencil = SpatialStencil.fromChunkNeighbors(pos, center, snapshot);
    double localGradient = Math.max(stencil.activationGradient,
        Math.max(stencil.memoryGradient, stencil.depthGradient));
    double localDensity = computeLocalDensity(pos, snapshot);
    double avgNeighborTransport = computeAverageTransport(pos, snapshot);
    double corridorStrength = computeCorridorStrength(pos, center, snapshot);
    double[] transportBias = computeTransportBias(pos, center, snapshot);

    double ecologicalStress = clamp01(readiness * 0.55 + localGradient * 8.0 + (1.0 - capacity) * 0.25);
    double environmentalStability = clamp01((1.0 - readiness) * 0.45 + capacity * 0.35 + convergence * 0.20);
    double migrationPressure = clamp01((1.0 - capacity) * 0.50 + localGradient * 4.0 + readiness * 0.20);
    double territorialPressure = clamp01(localGradient * 10.0 + activation * 0.25);
    double recoveryState = clamp01((1.0 - memory / Math.max(0.01, memory + 1.0)) * 0.40
        + capacity * 0.35 + convergence * 0.25);
    double prosperityPotential = clamp01(memory * 0.35 + capacity * 0.30 + fieldDepth * 0.20 + baseline * 0.15);
    double explorationTendency = clamp01(localGradient * 12.0 + transport * 0.30 + activation * 0.15);
    double conflictTendency = clamp01(localGradient * 14.0 + readiness * 0.30
        + Math.max(0.0, avgNeighborTransport - transport) * 0.20);
    double settlementTendency = clamp01(activation * 0.25 + memory * 0.25 + fieldDepth * 0.25 + localDensity * 0.25);
    double roadFormationTendency = clamp01(corridorStrength * 12.0 + transport * 0.35 + activation * 0.15);
    double tradeTendency = clamp01(corridorStrength * 10.0 + memory * 0.25 + capacity * 0.25);
    double expansionTendency = clamp01(readiness * 0.35 + localGradient * 8.0 + activation * 0.20);
    double collapseTendency = clamp01(memory * 0.35 + (1.0 - capacity) * 0.35 + readiness * 0.30);

    return new SubstrateSemantics(
        ecologicalStress, environmentalStability, migrationPressure, corridorStrength,
        territorialPressure, recoveryState, prosperityPotential, explorationTendency,
        conflictTendency, settlementTendency, roadFormationTendency, tradeTendency,
        expansionTendency, collapseTendency,
        localGradient, localDensity, avgNeighborTransport,
        transportBias[0], transportBias[1]);
  }

  public static GameplayIntent inferIntent(long tick, ChunkPos pos, SubstrateState center,
                                           SubstrateSemantics semantics, int persistenceTicks) {
    double act = center.getActivation();
    double mem = center.getMemory();
    double depth = center.getFieldDepth();
    double readiness = center.getReadiness();
    double trans = center.getTransport();

    if (semantics.collapseTendency > 0.72 && persistenceTicks >= 2) {
      return intent(tick, pos, IntentType.COLLAPSE, 0.82, "Ecological collapse: scar memory + low capacity", persistenceTicks);
    }
    if (semantics.recoveryState > 0.65 && mem > InterpretationThresholds.MEMORY_PERSIST && persistenceTicks >= 2) {
      return intent(tick, pos, IntentType.RECOVER, 0.78, "Recovery cycle: memory fading with rising capacity", persistenceTicks);
    }
    if (act >= InterpretationThresholds.ACTIVATION_PERSIST
        && mem >= InterpretationThresholds.MEMORY_PERSIST
        && depth >= InterpretationThresholds.FIELD_DEPTH_SETTLED
        && semantics.localDensity >= InterpretationThresholds.LOCAL_DENSITY_SETTLE
        && persistenceTicks >= InterpretationThresholds.PERSISTENCE_TICKS) {
      return intent(tick, pos, IntentType.CREATE_SETTLEMENT, 0.85,
          "Sustained activity + memory + depth + density", persistenceTicks);
    }
    if (act >= InterpretationThresholds.ACTIVATION_PERSIST
        && mem >= InterpretationThresholds.MEMORY_PERSIST * 2.0
        && persistenceTicks >= InterpretationThresholds.PERSISTENCE_TICKS) {
      return intent(tick, pos, IntentType.CONTINUE_MINING, 0.80,
          "Sustained extraction: activation + strong memory", persistenceTicks);
    }
    if (act >= InterpretationThresholds.ACTIVATION_PERSIST
        && semantics.corridorStrength >= InterpretationThresholds.TRANSPORT_CORRIDOR * 2.0
        && mem >= InterpretationThresholds.MEMORY_PERSIST
        && persistenceTicks >= InterpretationThresholds.PERSISTENCE_TICKS) {
      return intent(tick, pos, IntentType.INITIATE_TRADE, 0.75,
          "Bidirectional transport corridor + stable nodes", persistenceTicks);
    }
    if (semantics.corridorStrength >= InterpretationThresholds.TRANSPORT_CORRIDOR
        && act >= InterpretationThresholds.ACTIVATION_PERSIST * 0.5
        && persistenceTicks >= InterpretationThresholds.PERSISTENCE_TICKS) {
      return intent(tick, pos, IntentType.CREATE_ROAD, 0.70,
          "Persistent transport corridor", persistenceTicks);
    }
    if (act >= InterpretationThresholds.ACTIVATION_PERSIST
        && semantics.localGradient >= InterpretationThresholds.GRADIENT_FRONTIER
        && semantics.avgNeighborTransport > trans * 0.5
        && persistenceTicks >= 2) {
      return intent(tick, pos, IntentType.ENGAGE_COMBAT, 0.60,
          "Frontier pressure: gradient + opposed transport", persistenceTicks);
    }
    if (act >= InterpretationThresholds.ACTIVATION_PERSIST
        && readiness >= InterpretationThresholds.READINESS_HIGH
        && semantics.localGradient >= InterpretationThresholds.GRADIENT_FRONTIER * 0.5
        && mem >= InterpretationThresholds.MEMORY_PERSIST
        && persistenceTicks >= InterpretationThresholds.PERSISTENCE_TICKS) {
      return intent(tick, pos, IntentType.EXPAND_TERRITORY, 0.70,
          "Frontier expansion: readiness + gradient", persistenceTicks);
    }
    if (act >= InterpretationThresholds.ACTIVATION_PERSIST * 0.3
        && semantics.localGradient >= InterpretationThresholds.GRADIENT_FRONTIER * 0.3
        && persistenceTicks >= 2) {
      return intent(tick, pos, IntentType.EXPLORE, 0.50,
          "Frontier exploration along activation gradient", persistenceTicks);
    }
    if (act >= InterpretationThresholds.ACTIVATION_PERSIST * 0.2
        && act < InterpretationThresholds.ACTIVATION_PERSIST
        && trans > 0.0) {
      return intent(tick, pos, IntentType.WANDER, 0.30,
          "Weak transient movement along transport", persistenceTicks);
    }
    return null;
  }

  private static GameplayIntent intent(long tick, ChunkPos pos, IntentType type,
                                       double confidence, String reason, int persistenceTicks) {
    return new GameplayIntent(tick, pos.x, pos.z, type, confidence, reason, persistenceTicks);
  }

  private static double computeLocalDensity(ChunkPos pos, Map<ChunkPos, SubstrateState> snapshot) {
    int active = 0;
    int total = 0;
    for (int dx = -1; dx <= 1; dx++) {
      for (int dz = -1; dz <= 1; dz++) {
        ChunkPos neighbor = new ChunkPos(pos.x + dx, pos.z + dz);
        SubstrateState state = snapshot.get(neighbor);
        total++;
        if (state != null && state.getActivation() >= InterpretationThresholds.ACTIVATION_PERSIST) {
          active++;
        }
      }
    }
    return total > 0 ? (double) active / total : 0.0;
  }

  private static double computeAverageTransport(ChunkPos pos, Map<ChunkPos, SubstrateState> snapshot) {
    double sum = 0.0;
    int count = 0;
    for (int dx = -1; dx <= 1; dx++) {
      for (int dz = -1; dz <= 1; dz++) {
        ChunkPos neighbor = new ChunkPos(pos.x + dx, pos.z + dz);
        SubstrateState state = snapshot.get(neighbor);
        if (state != null) {
          sum += state.getTransport();
          count++;
        }
      }
    }
    return count > 0 ? sum / count : 0.0;
  }

  private static double computeCorridorStrength(ChunkPos pos, SubstrateState center,
                                                  Map<ChunkPos, SubstrateState> snapshot) {
    double centerTransport = center.getTransport();
    double incoming = 0.0;
    double outgoing = 0.0;
    int[][] dirs = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
    for (int[] dir : dirs) {
      ChunkPos neighbor = new ChunkPos(pos.x + dir[0], pos.z + dir[1]);
      SubstrateState state = snapshot.get(neighbor);
      if (state == null) {
        continue;
      }
      double neighborTransport = state.getTransport();
      incoming += Math.max(0.0, neighborTransport - centerTransport);
      outgoing += Math.max(0.0, centerTransport - neighborTransport);
    }
    return (incoming + outgoing) / 4.0;
  }

  private static double[] computeTransportBias(ChunkPos pos, SubstrateState center,
                                               Map<ChunkPos, SubstrateState> snapshot) {
    double biasX = 0.0;
    double biasZ = 0.0;
    double centerTransport = center.getTransport();
    int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    for (int[] dir : dirs) {
      ChunkPos neighbor = new ChunkPos(pos.x + dir[0], pos.z + dir[1]);
      SubstrateState state = snapshot.get(neighbor);
      if (state == null) {
        continue;
      }
      double delta = state.getTransport() - centerTransport;
      biasX += dir[0] * delta;
      biasZ += dir[1] * delta;
    }
    double mag = Math.hypot(biasX, biasZ);
    if (mag > 1e-6) {
      biasX /= mag;
      biasZ /= mag;
    }
    return new double[]{biasX, biasZ};
  }

  private static double clamp01(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }
}
