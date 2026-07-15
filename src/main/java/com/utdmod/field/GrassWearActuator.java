package com.utdmod.field;

import com.utdmod.interpretation.ChunkInterpretation;
import com.utdmod.interpretation.IntentType;
import com.utdmod.interpretation.SpatialBehaviorHelper;
import com.utdmod.interpretation.SubstrateSemantics;
import net.minecraft.server.world.ServerWorld;

/**
 * Transport-aligned persistent travel corridors expressed as worn paths across loaded chunks.
 */
public final class GrassWearActuator implements FieldActuator {

  @Override
  public com.utdmod.experiment.ActuatorOutcome actuate(ServerWorld world, ChunkInterpretation interpretation) {
    if (world == null || interpretation == null || interpretation.semantics == null) {
      return com.utdmod.experiment.ActuatorOutcome.THRESHOLD_NOT_MET;
    }
    if (!SpatialBehaviorHelper.isChunkLoaded(world, interpretation.chunkPos)) {
      return com.utdmod.experiment.ActuatorOutcome.THRESHOLD_NOT_MET;
    }

    SubstrateSemantics semantics = interpretation.semantics;
    IntentType intent = interpretation.primaryIntent != null ? interpretation.primaryIntent.type : null;
    if (intent != IntentType.CREATE_ROAD
        && semantics.roadFormationTendency < 0.45
        && semantics.corridorStrength < 0.02) {
      return com.utdmod.experiment.ActuatorOutcome.THRESHOLD_NOT_MET;
    }

    int steps = Math.max(1, (int) Math.ceil(semantics.roadFormationTendency * 8.0));
    if (intent == IntentType.INITIATE_TRADE || intent == IntentType.CREATE_ROAD) {
      steps = Math.max(steps, 4);
    }
    SpatialBehaviorHelper.wearCorridorSegment(world, interpretation.chunkPos, interpretation, steps);
    return com.utdmod.experiment.ActuatorOutcome.SUCCESS;
  }

  @Override
  public String getId() {
    return "grass_wear";
  }
}
