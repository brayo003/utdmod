package com.utdmod.interpretation;

import java.util.HashMap;
import java.util.Map;

/**
 * Maintains multi-tick intent persistence and priority selection per chunk.
 */
public final class BehaviorSelector {
  private static final IntentType[] PRIORITY_ORDER = {
      IntentType.CREATE_SETTLEMENT,
      IntentType.CONTINUE_MINING,
      IntentType.INITIATE_TRADE,
      IntentType.CREATE_ROAD,
      IntentType.ENGAGE_COMBAT,
      IntentType.EXPAND_TERRITORY,
      IntentType.RECOVER,
      IntentType.COLLAPSE,
      IntentType.EXPLORE,
      IntentType.WANDER
  };

  private final Map<String, Integer> persistenceByChunk = new HashMap<>();
  private final Map<String, IntentType> lastIntentTypeByChunk = new HashMap<>();

  public int persistenceFor(ChunkPosKey key) {
    return persistenceByChunk.getOrDefault(key.value(), 0);
  }

  public GameplayIntent select(long tick, ChunkPosKey key, GameplayIntent candidate) {
    if (candidate == null) {
      persistenceByChunk.put(key.value(), 0);
      lastIntentTypeByChunk.remove(key.value());
      return null;
    }

    IntentType lastType = lastIntentTypeByChunk.get(key.value());
    int count = persistenceByChunk.getOrDefault(key.value(), 0);
    if (lastType == candidate.type) {
      count++;
    } else {
      count = 1;
    }
    persistenceByChunk.put(key.value(), count);
    lastIntentTypeByChunk.put(key.value(), candidate.type);
    return new GameplayIntent(tick, candidate.chunkX, candidate.chunkZ, candidate.type,
        candidate.confidence, candidate.reason, count);
  }

  public static int priority(IntentType type) {
    for (int i = 0; i < PRIORITY_ORDER.length; i++) {
      if (PRIORITY_ORDER[i] == type) {
        return i;
      }
    }
    return Integer.MAX_VALUE;
  }

  public record ChunkPosKey(int x, int z) {
    public String value() {
      return x + "," + z;
    }
  }
}
