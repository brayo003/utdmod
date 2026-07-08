package com.utdmod.field;

import com.utdmod.interpretation.ChunkInterpretation;
import com.utdmod.interpretation.IntentType;
import com.utdmod.interpretation.SpatialBehaviorHelper;
import com.utdmod.interpretation.SubstrateSemantics;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * Drives mob ecology from interpreted substrate semantics and intents.
 */
public final class MobBehaviorActuator implements FieldActuator {
  private static final UUID UTD_SPEED = UUID.fromString("6d2c3b1d-0b0a-4c2e-9f1a-000000000001");
  private static final UUID UTD_FOLLOW = UUID.fromString("6d2c3b1d-0b0a-4c2e-9f1a-000000000002");

  @Override
  public void actuate(ServerWorld world, ChunkInterpretation interpretation) {
    if (world == null || interpretation == null || interpretation.semantics == null) {
      return;
    }
    if (!SpatialBehaviorHelper.isChunkLoaded(world, interpretation.chunkPos)) {
      return;
    }

    ChunkPos chunkPos = interpretation.chunkPos;
    SubstrateSemantics semantics = interpretation.semantics;
    IntentType intent = interpretation.primaryIntent != null ? interpretation.primaryIntent.type : null;

    Box chunkArea = new Box(
        chunkPos.getStartX(), world.getBottomY(), chunkPos.getStartZ(),
        chunkPos.getStartX() + 16, world.getTopY(), chunkPos.getStartZ() + 16);

    for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, chunkArea, MobEntity::isAlive)) {
      if (mob instanceof VillagerEntity || mob.getNavigation() == null) {
        continue;
      }
      if (mob instanceof AnimalEntity animal) {
        actuatePassive(world, animal, semantics, intent, interpretation);
      } else if (mob instanceof HostileEntity hostile) {
        actuateHostile(world, hostile, semantics, intent, interpretation);
      }
    }
  }

  private void actuatePassive(ServerWorld world, AnimalEntity mob, SubstrateSemantics semantics, IntentType intent,
                              ChunkInterpretation interpretation) {
    if (semantics.ecologicalStress > 0.65 || intent == IntentType.ENGAGE_COMBAT) {
      moveTo(mob, SpatialBehaviorHelper.fleeFromCorridor(mob.getPos(), interpretation, 16.0), 1.15);
      return;
    }
    if (semantics.migrationPressure > 0.55) {
      moveTo(mob, SpatialBehaviorHelper.corridorTarget(world, mob.getPos(), interpretation, 24.0), 1.0);
      return;
    }
    if (intent == IntentType.EXPLORE || semantics.explorationTendency > 0.45) {
      moveTo(mob, SpatialBehaviorHelper.corridorTarget(world, mob.getPos(), interpretation, 18.0), 0.9);
      return;
    }
    if (semantics.prosperityPotential > 0.55 && semantics.environmentalStability > 0.45) {
      if (mob.getBreedingAge() > 0) {
        mob.setBreedingAge(0);
      }
      if (mob.isInLove()) {
        mob.lovePlayer(null);
      }
    }
    if (intent == IntentType.WANDER || semantics.corridorStrength > 0.02) {
      moveTo(mob, SpatialBehaviorHelper.corridorTarget(world, mob.getPos(), interpretation, 10.0), 0.75);
    }
  }

  private void actuateHostile(ServerWorld world, HostileEntity mob, SubstrateSemantics semantics, IntentType intent,
                            ChunkInterpretation interpretation) {
    applyStressModifiers(mob, semantics);
    if (intent == IntentType.ENGAGE_COMBAT || semantics.conflictTendency > 0.55) {
      mob.setTarget(null);
      moveTo(mob, SpatialBehaviorHelper.corridorTarget(world, mob.getPos(), interpretation, 20.0), 1.2);
      return;
    }
    if (semantics.environmentalStability > 0.7 && semantics.conflictTendency < 0.25) {
      moveTo(mob, SpatialBehaviorHelper.fleeFromCorridor(mob.getPos(), interpretation, 16.0), 0.85);
    }
  }

  private static void moveTo(MobEntity mob, Vec3d target, double speed) {
    if (mob.getNavigation() == null || target == null) {
      return;
    }
    mob.getNavigation().startMovingTo(target.x, target.y, target.z, speed);
  }

  private void applyStressModifiers(MobEntity mob, SubstrateSemantics semantics) {
    EntityAttributeInstance move = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
    EntityAttributeInstance follow = mob.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
    double speedAdd = semantics.ecologicalStress * 0.08 + semantics.conflictTendency * 0.05;
    double followAdd = semantics.territorialPressure * 8.0;
    if (move != null && move.getModifier(UTD_SPEED) == null && speedAdd > 0.01) {
      move.addTemporaryModifier(new EntityAttributeModifier(UTD_SPEED, "utd_substrate_stress_speed",
          speedAdd, EntityAttributeModifier.Operation.ADDITION));
    }
    if (follow != null && follow.getModifier(UTD_FOLLOW) == null && followAdd > 0.5) {
      follow.addTemporaryModifier(new EntityAttributeModifier(UTD_FOLLOW, "utd_substrate_territory_follow",
          followAdd, EntityAttributeModifier.Operation.ADDITION));
    }
  }

  @Override
  public String getId() {
    return "mob_behavior";
  }
}
