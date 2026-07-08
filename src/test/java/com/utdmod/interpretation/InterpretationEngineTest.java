package com.utdmod.interpretation;

import com.utdmod.tension.SubstrateState;
import net.minecraft.util.math.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class InterpretationEngineTest {

  @Test
  void semanticsDeriveFromSubstrateWithoutMutatingState() {
    SubstrateState center = new SubstrateState(0.2, 0.15, 0.05, 0.03, 0.01);
    Map<ChunkPos, SubstrateState> snapshot = new HashMap<>();
    ChunkPos pos = new ChunkPos(0, 0);
    snapshot.put(pos, center);
    snapshot.put(new ChunkPos(1, 0), new SubstrateState(0.35, 0.1, 0.08, 0.02));
    snapshot.put(new ChunkPos(-1, 0), new SubstrateState(0.05, 0.05, 0.01, 0.01));

    double beforeActivation = center.getActivation();
    SubstrateSemantics semantics = SubstrateInterpretationEngine.computeSemantics(pos, center, snapshot);
    assertEquals(beforeActivation, center.getActivation(), 1e-9);
    assertNotNull(semantics);
    assert semantics.localGradient >= 0.0;
    assert semantics.corridorStrength >= 0.0;
  }

  @Test
  void behaviorSelectorTracksPersistence() {
    BehaviorSelector selector = new BehaviorSelector();
    BehaviorSelector.ChunkPosKey key = new BehaviorSelector.ChunkPosKey(1, 2);
    GameplayIntent wander = new GameplayIntent(10, 1, 2, IntentType.WANDER, 0.3, "wander", 1);
    GameplayIntent selected = selector.select(10, key, wander);
    assertNotNull(selected);
    assertEquals(1, selected.persistenceTicks);
    selected = selector.select(15, key, wander);
    assertEquals(2, selected.persistenceTicks);
  }

  @Test
  void corridorStrengthRisesWithNeighborTransportContrast() {
    SubstrateState center = new SubstrateState(0.05, 0.05, 0.01, 0.02);
    Map<ChunkPos, SubstrateState> snapshot = new HashMap<>();
    ChunkPos pos = new ChunkPos(0, 0);
    snapshot.put(pos, center);
    snapshot.put(new ChunkPos(1, 0), new SubstrateState(0.05, 0.05, 0.12, 0.02));
    SubstrateSemantics semantics = SubstrateInterpretationEngine.computeSemantics(pos, center, snapshot);
    assert semantics.corridorStrength > 0.0;
    assert semantics.transportBiasX != 0.0 || semantics.transportBiasZ != 0.0;
  }

  @Test
  void inferIntentNeverMutatesSubstrate() {
    SubstrateState center = new SubstrateState(0.2, 0.15, 0.05, 0.03, 0.01);
    Map<ChunkPos, SubstrateState> snapshot = Map.of(new ChunkPos(0, 0), center);
    ChunkPos pos = new ChunkPos(0, 0);
    double before = center.getActivation();
    SubstrateSemantics semantics = SubstrateInterpretationEngine.computeSemantics(pos, center, snapshot);
    SubstrateInterpretationEngine.inferIntent(10, pos, center, semantics, 3);
    assertEquals(before, center.getActivation(), 1e-9);
  }
}
