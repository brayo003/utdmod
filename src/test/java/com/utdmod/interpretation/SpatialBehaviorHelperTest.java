package com.utdmod.interpretation;

import com.utdmod.tension.SubstrateState;
import net.minecraft.util.math.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class SpatialBehaviorHelperTest {

  @Test
  void corridorDirectionUsesLocalBiasWithoutNeighbors() {
    SubstrateState center = new SubstrateState(0.1, 0.05, 0.04, 0.02);
    Map<ChunkPos, SubstrateState> snapshot = new HashMap<>();
    ChunkPos pos = new ChunkPos(0, 0);
    snapshot.put(pos, center);
    snapshot.put(new ChunkPos(1, 0), new SubstrateState(0.05, 0.05, 0.12, 0.02));
    SubstrateSemantics semantics = SubstrateInterpretationEngine.computeSemantics(pos, center, snapshot);
    ChunkInterpretation interpretation = new ChunkInterpretation(
        pos, center,
        com.utdmod.tension.SpatialStencil.fromChunkNeighbors(pos, center, snapshot),
        semantics, null);
    SpatialBehaviorHelper.CorridorDirection dir = SpatialBehaviorHelper.resolveCorridorDirection(null, interpretation);
    assertNotEquals(0, dir.stepX() + dir.stepZ());
  }

  @Test
  void resolveCorridorDirectionNeverMutatesSubstrate() {
    SubstrateState center = new SubstrateState(0.2, 0.1, 0.05, 0.03);
    double before = center.getActivation();
    ChunkPos pos = new ChunkPos(3, 4);
    Map<ChunkPos, SubstrateState> snapshot = Map.of(pos, center);
    SubstrateSemantics semantics = SubstrateInterpretationEngine.computeSemantics(pos, center, snapshot);
    ChunkInterpretation interpretation = new ChunkInterpretation(
        pos, center,
        com.utdmod.tension.SpatialStencil.fromChunkNeighbors(pos, center, snapshot),
        semantics, null);
    SpatialBehaviorHelper.resolveCorridorDirection(null, interpretation);
    assertEquals(before, center.getActivation(), 1e-9);
  }

  @Test
  void isChunkLoadedReturnsFalseForNullWorld() {
    assertFalse(SpatialBehaviorHelper.isChunkLoaded(null, new ChunkPos(0, 0)));
    assertFalse(SpatialBehaviorHelper.isChunkLoaded(null, 0, 0));
  }
}
