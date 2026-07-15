package com.utdmod.field;

import com.utdmod.experiment.ActuatorOutcome;
import com.utdmod.experiment.ExperimentLogger;
import com.utdmod.interpretation.ChunkInterpretation;
import com.utdmod.interpretation.IntentType;
import com.utdmod.interpretation.SpatialBehaviorHelper;
import com.utdmod.interpretation.SubstrateSemantics;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Village evolution driven by interpreted prosperity, collapse, trade, and settlement intents.
 */
public final class VillageActuator implements FieldActuator {

  @Override
  public com.utdmod.experiment.ActuatorOutcome actuate(ServerWorld world, ChunkInterpretation interpretation) {
    if (world == null || interpretation == null || interpretation.semantics == null) {
      return com.utdmod.experiment.ActuatorOutcome.THRESHOLD_NOT_MET;
    }
    if (!SpatialBehaviorHelper.isChunkLoaded(world, interpretation.chunkPos)) {
      return com.utdmod.experiment.ActuatorOutcome.THRESHOLD_NOT_MET;
    }

    ChunkPos chunkPos = interpretation.chunkPos;
    SubstrateSemantics semantics = interpretation.semantics;
    IntentType intent = interpretation.primaryIntent != null ? interpretation.primaryIntent.type : null;

    Box chunkArea = new Box(
        chunkPos.getStartX(), world.getBottomY(), chunkPos.getStartZ(),
        chunkPos.getStartX() + 16, world.getTopY(), chunkPos.getStartZ() + 16);
    List<VillagerEntity> villagers = world.getEntitiesByClass(
        VillagerEntity.class, chunkArea, v -> v.isAlive() && v.getNavigation() != null);

    if (villagers.isEmpty()) return com.utdmod.experiment.ActuatorOutcome.NO_TARGET;
    for (VillagerEntity villager : villagers) {
      actuateVillager(world, villager, interpretation, semantics, intent);
    }
    return com.utdmod.experiment.ActuatorOutcome.SUCCESS;
  }

  private void actuateVillager(ServerWorld world, VillagerEntity villager, ChunkInterpretation interpretation,
                               SubstrateSemantics semantics, IntentType intent) {
    if (intent == IntentType.COLLAPSE || semantics.collapseTendency > 0.7) {
      villager.setBreedingAge(6000);
      villager.getNavigation().stop();
      ExperimentLogger.logActuatorDecision(world.getTime(), world, interpretation.chunkPos, getId(),
          interpretation.substrate, "VILLAGE_COLLAPSE", "collapse tendency",
          "breeding paused; hold position", "", null, 0L, ActuatorOutcome.SUCCESS);
      return;
    }
    if (intent == IntentType.RECOVER || semantics.recoveryState > 0.6) {
      if (villager.getBreedingAge() > 0) {
        villager.setBreedingAge(Math.max(0, villager.getBreedingAge() - 200));
      }
    }
    if (intent == IntentType.CREATE_SETTLEMENT || semantics.settlementTendency > 0.55) {
      if (villager.getBreedingAge() > 0) {
        villager.setBreedingAge(0);
      }
      villager.restock();
      ExperimentLogger.logActuatorDecision(world.getTime(), world, interpretation.chunkPos, getId(),
          interpretation.substrate, "SETTLEMENT_GROWTH", "settlement tendency",
          "breeding enabled; restock", "", null, 0L, ActuatorOutcome.SUCCESS);
    }
    if (intent == IntentType.INITIATE_TRADE || semantics.tradeTendency > 0.5) {
      villager.restock();
      Vec3d tradeTarget = SpatialBehaviorHelper.corridorTarget(world, villager.getPos(), interpretation, 32.0);
      villager.getNavigation().startMovingTo(tradeTarget.x, tradeTarget.y, tradeTarget.z, 0.85);
      return;
    }
    if (semantics.migrationPressure > 0.6 && semantics.prosperityPotential < 0.35) {
      villager.setBreedingAge(6000);
      Vec3d migration = SpatialBehaviorHelper.corridorTarget(world, villager.getPos(), interpretation, 28.0);
      villager.getNavigation().startMovingTo(migration.x, migration.y, migration.z, 0.9);
      return;
    }
    if (semantics.ecologicalStress > 0.65) {
      villager.getNavigation().stop();
      return;
    }
    if (semantics.prosperityPotential > 0.45 && semantics.environmentalStability > 0.4) {
      if (villager.getBreedingAge() > 0) {
        villager.setBreedingAge(0);
      }
    }
  }

  @Override
  public String getId() {
    return "village";
  }
}
