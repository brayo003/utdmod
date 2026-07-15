package com.utdmod.field;

import com.utdmod.interpretation.ChunkInterpretation;
import com.utdmod.interpretation.SpatialBehaviorHelper;
import com.utdmod.interpretation.SubstrateSemantics;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

/**
 * Subtle environmental particles — never debug overlays.
 */
public final class ParticleVisualActuator implements FieldActuator {

  @Override
  public com.utdmod.experiment.ActuatorOutcome actuate(ServerWorld world, ChunkInterpretation interpretation) {
    if (world == null || interpretation == null || interpretation.semantics == null
        || interpretation.chunkPos == null) {
      return com.utdmod.experiment.ActuatorOutcome.THRESHOLD_NOT_MET;
    }
    if (!SpatialBehaviorHelper.isChunkLoaded(world, interpretation.chunkPos)) {
      return com.utdmod.experiment.ActuatorOutcome.THRESHOLD_NOT_MET;
    }

    SubstrateSemantics semantics = interpretation.semantics;
    ChunkPos chunkPos = interpretation.chunkPos;
    BlockPos center = new BlockPos(chunkPos.getStartX() + 8, world.getSeaLevel(), chunkPos.getStartZ() + 8);

    boolean spawned = false;
    if (semantics.ecologicalStress > 0.55) {
      spawn(world, center, ParticleTypes.SMOKE, (int) (semantics.ecologicalStress * 3));
      spawned = true;
    }
    if (semantics.prosperityPotential > 0.5) {
      spawn(world, center, ParticleTypes.HAPPY_VILLAGER, (int) (semantics.prosperityPotential * 2));
      spawned = true;
    }
    if (semantics.corridorStrength > 0.03) {
      var dir = SpatialBehaviorHelper.resolveCorridorDirection(world, interpretation);
      int offsetX = dir != null ? dir.stepX() * 3 : (int) semantics.transportBiasX;
      int offsetZ = dir != null ? dir.stepZ() * 3 : (int) semantics.transportBiasZ;
      BlockPos drift = new BlockPos(center.getX() + offsetX, center.getY(), center.getZ() + offsetZ);
      if (SpatialBehaviorHelper.isChunkLoaded(world, drift.getX(), drift.getZ())) {
        spawn(world, drift, ParticleTypes.CLOUD, 1);
        spawned = true;
      }
    }
    if (semantics.recoveryState > 0.55) {
      spawn(world, center, ParticleTypes.COMPOSTER, 1);
      spawned = true;
    }
    if (semantics.collapseTendency > 0.6) {
      spawn(world, center, ParticleTypes.ASH, 2);
      spawned = true;
    }
    return spawned ? com.utdmod.experiment.ActuatorOutcome.SUCCESS
                   : com.utdmod.experiment.ActuatorOutcome.THRESHOLD_NOT_MET;
  }

  private void spawn(ServerWorld world, BlockPos center, ParticleEffect particle, int count) {
    if (!SpatialBehaviorHelper.isChunkLoaded(world, center.getX(), center.getZ())) {
      return;
    }
    for (int i = 0; i < count; i++) {
      world.spawnParticles(particle,
          center.getX() + (i % 3) - 1,
          center.getY() + 1.0,
          center.getZ() + ((i / 3) % 3) - 1,
          1, 0, 0.02, 0, 0.01);
    }
  }

  @Override
  public String getId() {
    return "particle_visual";
  }
}
