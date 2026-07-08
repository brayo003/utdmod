package com.utdmod.interpretation;

import com.utdmod.core.TensionTraceClock;
import com.utdmod.experiment.ExperimentLogger;
import com.utdmod.tension.ChunkTensionData;
import com.utdmod.tension.SubstrateState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-world interpretation coordinator: batch-computes semantics and intents after substrate integration.
 */
public final class InterpretationService {
  private static final Map<ServerWorld, BehaviorSelector> SELECTORS = new HashMap<>();
  private static final Map<ServerWorld, Map<ChunkPos, ChunkInterpretation>> LATEST = new HashMap<>();

  private InterpretationService() {}

  public static Map<ChunkPos, ChunkInterpretation> interpretWorld(ServerWorld world,
                                                                  Iterable<ChunkPos> positions) {
    ChunkTensionData data = ChunkTensionData.getServerState(world);
    Map<ChunkPos, SubstrateState> snapshot = new HashMap<>();
    for (Map.Entry<ChunkPos, SubstrateState> entry : data.getSubstrateStatesView().entrySet()) {
      snapshot.put(entry.getKey(), entry.getValue().snapshotCopy());
    }
    for (ChunkPos pos : positions) {
      snapshot.computeIfAbsent(pos, ignored -> data.getOrCreateSubstrateState(pos).snapshotCopy());
    }

    BehaviorSelector selector = SELECTORS.computeIfAbsent(world, ignored -> new BehaviorSelector());
    Map<ChunkPos, ChunkInterpretation> interpretations = new HashMap<>();
    long tick = TensionTraceClock.getServerTick();

    for (ChunkPos pos : positions) {
      if (!world.isChunkLoaded(pos.x, pos.z)) {
        continue;
      }
      SubstrateState substrate = snapshot.get(pos);
      if (substrate == null) {
        continue;
      }
      BehaviorSelector.ChunkPosKey key = new BehaviorSelector.ChunkPosKey(pos.x, pos.z);
      int persistence = selector.persistenceFor(key);
      SubstrateSemantics semantics = SubstrateInterpretationEngine.computeSemantics(pos, substrate, snapshot);
      GameplayIntent candidate = SubstrateInterpretationEngine.inferIntent(tick, pos, substrate, semantics, persistence);
      GameplayIntent intent = selector.select(tick, key, candidate);
      ChunkInterpretation interpretation = new ChunkInterpretation(
          pos, substrate, com.utdmod.tension.SpatialStencil.fromChunkNeighbors(pos, substrate, snapshot),
          semantics, intent);
      interpretations.put(pos, interpretation);
      ExperimentLogger.logInterpretationSnapshot(tick, world, interpretation);
    }

    LATEST.put(world, interpretations);
    return interpretations;
  }

  public static ChunkInterpretation get(ServerWorld world, ChunkPos pos) {
    Map<ChunkPos, ChunkInterpretation> map = LATEST.get(world);
    return map != null ? map.get(pos) : null;
  }

  public static Map<ChunkPos, ChunkInterpretation> latestForWorld(ServerWorld world) {
    return LATEST.getOrDefault(world, Map.of());
  }

  public static void clearWorld(ServerWorld world) {
    SELECTORS.remove(world);
    LATEST.remove(world);
  }
}
