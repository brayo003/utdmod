package com.utdmod.interpretation;

/**
 * A single semantic intent for one chunk, produced read-only from substrate state.
 */
public final class GameplayIntent {
  public final long tick;
  public final int chunkX;
  public final int chunkZ;
  public final IntentType type;
  public final double confidence;
  public final String reason;
  public final int persistenceTicks;

  public GameplayIntent(long tick, int chunkX, int chunkZ, IntentType type,
                        double confidence, String reason, int persistenceTicks) {
    this.tick = tick;
    this.chunkX = chunkX;
    this.chunkZ = chunkZ;
    this.type = type;
    this.confidence = confidence;
    this.reason = reason;
    this.persistenceTicks = persistenceTicks;
  }
}
