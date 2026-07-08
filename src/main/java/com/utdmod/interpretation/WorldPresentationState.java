package com.utdmod.interpretation;

import com.utdmod.network.PresentationSyncPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

import java.util.Map;

/**
 * Aggregates interpreted semantics for player-facing presentation (non-HUD).
 */
public final class WorldPresentationState {
  private static double globalEcologicalStress;
  private static double globalStability;
  private static double globalProsperity;
  private static double globalRecovery;
  private static int corridorChunks;
  private static int settlementChunks;
  private static double transportBiasX;
  private static double transportBiasZ;

  private WorldPresentationState() {}

  public static void update(ServerWorld world, Map<ChunkPos, ChunkInterpretation> interpretations) {
    if (interpretations.isEmpty()) {
      return;
    }
    double stress = 0.0;
    double stability = 0.0;
    double prosperity = 0.0;
    double recovery = 0.0;
    double biasX = 0.0;
    double biasZ = 0.0;
    int corridors = 0;
    int settlements = 0;
    int count = 0;
    for (ChunkInterpretation interpretation : interpretations.values()) {
      SubstrateSemantics semantics = interpretation.semantics;
      stress += semantics.ecologicalStress;
      stability += semantics.environmentalStability;
      prosperity += semantics.prosperityPotential;
      recovery += semantics.recoveryState;
      biasX += semantics.transportBiasX;
      biasZ += semantics.transportBiasZ;
      if (semantics.corridorStrength > InterpretationThresholds.TRANSPORT_CORRIDOR) {
        corridors++;
      }
      if (semantics.settlementTendency > 0.55) {
        settlements++;
      }
      count++;
    }
    globalEcologicalStress = stress / count;
    globalStability = stability / count;
    globalProsperity = prosperity / count;
    globalRecovery = recovery / count;
    transportBiasX = biasX / count;
    transportBiasZ = biasZ / count;
    corridorChunks = corridors;
    settlementChunks = settlements;

    MinecraftServer server = world.getServer();
    if (server != null && server.getTicks() % 40 == 0) {
      PresentationSyncPacket.broadcast(server, globalEcologicalStress, globalStability, globalProsperity,
          globalRecovery, corridorChunks, settlementChunks, transportBiasX, transportBiasZ);
    }
  }

  public static double ecologicalStress() { return globalEcologicalStress; }
  public static double stability() { return globalStability; }
  public static double prosperity() { return globalProsperity; }
  public static double recovery() { return globalRecovery; }
  public static int corridorChunks() { return corridorChunks; }
  public static int settlementChunks() { return settlementChunks; }
}
