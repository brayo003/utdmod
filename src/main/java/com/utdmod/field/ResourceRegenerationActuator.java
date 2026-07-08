package com.utdmod.field;

import com.utdmod.experiment.ActuatorOutcome;
import com.utdmod.experiment.ExperimentLogger;
import com.utdmod.interpretation.ChunkInterpretation;
import com.utdmod.interpretation.IntentType;
import com.utdmod.interpretation.SpatialBehaviorHelper;
import com.utdmod.interpretation.SubstrateSemantics;
import com.utdmod.tension.SubstrateState;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

/**
 * Resource succession from interpreted mining memory, recovery, and prosperity semantics.
 */
public final class ResourceRegenerationActuator implements FieldActuator {

  @Override
  public void actuate(ServerWorld world, ChunkInterpretation interpretation) {
    if (world == null || interpretation == null || interpretation.semantics == null
        || interpretation.substrate == null) {
      return;
    }
    if (!com.utdmod.interpretation.SpatialBehaviorHelper.isChunkLoaded(world, interpretation.chunkPos)) {
      return;
    }

    ChunkPos chunkPos = interpretation.chunkPos;

    SubstrateState substrate = interpretation.substrate;
    SubstrateSemantics semantics = interpretation.semantics;
    IntentType intent = interpretation.primaryIntent != null ? interpretation.primaryIntent.type : null;

    double depth = substrate.getFieldDepth();
    double memory = substrate.getMemory();
    double activation = substrate.getActivation();
    double capacity = substrate.getCapacity();

    double miningPotential = depth * 0.35 + memory * 0.40 + activation * 0.25;
    double successionPotential = semantics.recoveryState * 0.45 + capacity * 0.35 + semantics.prosperityPotential * 0.20;

    if (intent == IntentType.CONTINUE_MINING || miningPotential > 0.45) {
      regenerateOres(world, chunkPos, miningPotential, substrate, semantics);
    }
    if (intent == IntentType.RECOVER || successionPotential > 0.42) {
      regenerateCrops(world, chunkPos, successionPotential, substrate);
      regenerateSurfaceRecovery(world, chunkPos, semantics);
    }
  }

  private void regenerateOres(ServerWorld world, ChunkPos chunkPos, double potential,
                              SubstrateState substrate, SubstrateSemantics semantics) {
    int baseX = chunkPos.getStartX();
    int baseZ = chunkPos.getStartZ();
    var dir = SpatialBehaviorHelper.resolveCorridorDirection(world,
        com.utdmod.interpretation.InterpretationService.get(world, chunkPos));
    int biasX = dir != null ? dir.stepX() * 7 : (int) Math.round(semantics.transportBiasX * 7.0);
    int biasZ = dir != null ? dir.stepZ() * 7 : (int) Math.round(semantics.transportBiasZ * 7.0);
    int x = baseX + Math.floorMod(8 + biasX, 16);
    int z = baseZ + Math.floorMod(8 + biasZ, 16);
    int y = -32 + Math.min(31, Math.max(0, (int) Math.floor(potential * 32)));

    BlockPos pos = new BlockPos(x, y, z);
    if (!SpatialBehaviorHelper.isChunkLoaded(world, x, z)) {
      return;
    }
    Block existing = world.getBlockState(pos).getBlock();
    if (existing == Blocks.STONE || existing == Blocks.DEEPSLATE) {
      Block ore = selectOreType(potential);
      world.setBlockState(pos, ore.getDefaultState());
      ExperimentLogger.logActuatorDecision(world.getTime(), world, chunkPos, getId(), substrate,
          "ORE_CRYSTALLIZATION", "mining memory + depth along transport bias",
          "setBlockState(" + ore.getTranslationKey() + ")", ore.getTranslationKey(), pos, 0L,
          ActuatorOutcome.SUCCESS);
    }
  }

  private Block selectOreType(double potential) {
    if (potential > 0.9) return Blocks.DIAMOND_ORE;
    if (potential > 0.8) return Blocks.EMERALD_ORE;
    if (potential > 0.7) return Blocks.GOLD_ORE;
    if (potential > 0.6) return Blocks.LAPIS_ORE;
    return Blocks.IRON_ORE;
  }

  private void regenerateCrops(ServerWorld world, ChunkPos chunkPos, double potential, SubstrateState substrate) {
    int baseX = chunkPos.getStartX();
    int baseZ = chunkPos.getStartZ();
    int growthSteps = Math.min(5, Math.max(1, (int) Math.floor(potential * 10)));
    for (int dx = 0; dx < 4; dx++) {
      for (int dz = 0; dz < 4; dz++) {
        BlockPos surface = new BlockPos(baseX + dx, world.getTopY(), baseZ + dz);
        for (int y = surface.getY(); y > 48; y--) {
          BlockPos below = new BlockPos(surface.getX(), y, surface.getZ());
          Block block = world.getBlockState(below).getBlock();
          if (block == Blocks.WHEAT || block == Blocks.CARROTS || block == Blocks.POTATOES) {
            BlockState state = world.getBlockState(below);
            Property<Integer> ageProperty = findAgeProperty(state);
            if (ageProperty == null || !state.contains(ageProperty)) {
              break;
            }
            for (int step = 0; step < growthSteps; step++) {
              int age = state.get(ageProperty);
              if (age < 7) {
                state = state.with(ageProperty, Math.min(7, age + 1));
                world.setBlockState(below, state);
              }
            }
            break;
          }
        }
      }
    }
  }

  private void regenerateSurfaceRecovery(ServerWorld world, ChunkPos chunkPos, SubstrateSemantics semantics) {
    if (semantics.recoveryState < 0.5) {
      return;
    }
    int x = chunkPos.getStartX() + 4;
    int z = chunkPos.getStartZ() + 4;
    for (int y = 60; y < 100; y++) {
      BlockPos pos = new BlockPos(x, y, z);
      if (world.getBlockState(pos).getBlock() == Blocks.COARSE_DIRT) {
        world.setBlockState(pos, Blocks.GRASS_BLOCK.getDefaultState());
        break;
      }
    }
  }

  @SuppressWarnings("unchecked")
  private Property<Integer> findAgeProperty(BlockState state) {
    for (Property<?> property : state.getProperties()) {
      if ("age".equals(property.getName())) {
        return (Property<Integer>) property;
      }
    }
    return null;
  }

  @Override
  public String getId() {
    return "resource_regeneration";
  }
}
