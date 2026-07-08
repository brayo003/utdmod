package com.utdmod.research;

import com.utdmod.tension.ChunkTensionData;
import com.utdmod.tension.SubstrateState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight replay helper for longitudinal substrate comparison from experiment logs.
 * Full world replay is not possible without chunk unload semantics; this supports snapshot diff studies.
 */
public final class ReplayAnalysisSupport {
  private ReplayAnalysisSupport() {}

  public static Map<ChunkPos, SubstrateState> captureSubstrateSnapshot(ServerWorld world) {
    ChunkTensionData data = ChunkTensionData.getServerState(world);
    Map<ChunkPos, SubstrateState> snapshot = new HashMap<>();
    for (Map.Entry<ChunkPos, SubstrateState> entry : data.getSubstrateStatesView().entrySet()) {
      snapshot.put(entry.getKey(), entry.getValue().snapshotCopy());
    }
    return snapshot;
  }

  public static List<String> compareSnapshots(Map<ChunkPos, SubstrateState> before,
                                              Map<ChunkPos, SubstrateState> after,
                                              double activationEpsilon) {
    List<String> deltas = new ArrayList<>();
    for (Map.Entry<ChunkPos, SubstrateState> entry : after.entrySet()) {
      SubstrateState previous = before.get(entry.getKey());
      if (previous == null) {
        deltas.add("new_chunk " + entry.getKey().x + "," + entry.getKey().z);
        continue;
      }
      SubstrateState current = entry.getValue();
      if (Math.abs(current.getActivation() - previous.getActivation()) > activationEpsilon) {
        deltas.add(String.format("chunk %d,%d activation %.4f -> %.4f",
            entry.getKey().x, entry.getKey().z, previous.getActivation(), current.getActivation()));
      }
      if (Math.abs(current.getMemory() - previous.getMemory()) > activationEpsilon) {
        deltas.add(String.format("chunk %d,%d memory %.4f -> %.4f",
            entry.getKey().x, entry.getKey().z, previous.getMemory(), current.getMemory()));
      }
    }
    return deltas;
  }

  public static void logComparison(MinecraftServer server, String label) {
    for (ServerWorld world : server.getWorlds()) {
      Map<ChunkPos, SubstrateState> snapshot = captureSubstrateSnapshot(world);
      System.out.printf("[REPLAY_ANALYSIS] %s world=%s chunks=%d%n",
          label, world.getRegistryKey().getValue(), snapshot.size());
    }
  }
}
