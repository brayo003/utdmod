package com.utdmod.research.player;

/**
 * Seeded LCG — all bot decisions derive from world seed xor bot seed, never wall clock.
 */
public final class DeterministicRandom {
  private long state;

  public DeterministicRandom(long seed) {
    this.state = seed == 0 ? 0x4D59544DF26D6175L : seed;
  }

  public static DeterministicRandom fromSeeds(long worldSeed, int botSeed) {
    long mixed = worldSeed ^ ((long) botSeed * 0x9E3779B97F4A7C15L);
    mixed ^= (mixed >>> 33);
    mixed *= 0xff51afd7ed558ccdL;
    mixed ^= (mixed >>> 33);
    return new DeterministicRandom(mixed);
  }

  public long nextLong() {
    state = state * 6364136223846793005L + 1442695040888963407L;
    return state;
  }

  public int nextInt(int bound) {
    if (bound <= 0) {
      return 0;
    }
    long raw = nextLong() >>> 1;
    return (int) (raw % bound);
  }

  public double nextDouble() {
    return (nextLong() >>> 11) * 0x1.0p-53;
  }

  public long getState() {
    return state;
  }

  public void setState(long state) {
    this.state = state;
  }
}
