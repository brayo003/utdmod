package com.utdmod.research.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class ResearchPlayerDeterminismTest {

  @Test
  void sameSeedsProduceSameSequence() {
    DeterministicRandom a = DeterministicRandom.fromSeeds(12345L, 7);
    DeterministicRandom b = DeterministicRandom.fromSeeds(12345L, 7);
    for (int i = 0; i < 100; i++) {
      assertEquals(a.nextLong(), b.nextLong());
    }
  }

  @Test
  void differentBotSeedChangesSequence() {
    DeterministicRandom a = DeterministicRandom.fromSeeds(12345L, 1);
    DeterministicRandom b = DeterministicRandom.fromSeeds(12345L, 2);
    assertNotEquals(a.nextLong(), b.nextLong());
  }

  @Test
  void differentWorldSeedChangesSequence() {
    DeterministicRandom a = DeterministicRandom.fromSeeds(100L, 5);
    DeterministicRandom b = DeterministicRandom.fromSeeds(200L, 5);
    assertNotEquals(a.nextLong(), b.nextLong());
  }

  @Test
  void stateRoundTripPreservesSequence() {
    DeterministicRandom rng = DeterministicRandom.fromSeeds(999L, 3);
    rng.nextLong();
    rng.nextLong();
    long saved = rng.getState();
    long next = rng.nextLong();
    DeterministicRandom restored = new DeterministicRandom(0);
    restored.setState(saved);
    assertEquals(next, restored.nextLong());
  }

  @Test
  void stateEnumParse() {
    assertEquals(ResearchPlayerState.MINE_IRON, ResearchPlayerState.parse("mine_iron"));
    assertEquals(ResearchPlayerState.GATHER_WOOD, ResearchPlayerState.parse("GATHER-WOOD"));
  }
}
