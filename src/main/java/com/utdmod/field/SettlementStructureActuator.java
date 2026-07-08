package com.utdmod.field;

import com.utdmod.experiment.ActuatorOutcome;
import com.utdmod.experiment.ExperimentLogger;
import com.utdmod.interpretation.ChunkInterpretation;
import com.utdmod.interpretation.IntentType;
import com.utdmod.interpretation.InterpretationService;
import com.utdmod.interpretation.SpatialBehaviorHelper;
import com.utdmod.interpretation.SubstrateSemantics;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

/**
 * Expresses settlement and expansion intents as subtle world structure — not debug markers.
 * Campfires, hay, cobble foundations emerge where sustained substrate memory and depth indicate habitation.
 */
public final class SettlementStructureActuator implements FieldActuator {

  @Override
  public void actuate(ServerWorld world, ChunkInterpretation interpretation) {
    if (world == null || interpretation == null || interpretation.semantics == null) {
      return;
    }
    if (!SpatialBehaviorHelper.isChunkLoaded(world, interpretation.chunkPos)) {
      return;
    }

    SubstrateSemantics semantics = interpretation.semantics;
    IntentType intent = interpretation.primaryIntent != null ? interpretation.primaryIntent.type : null;
    ChunkPos chunkPos = interpretation.chunkPos;

    if (intent == IntentType.CREATE_SETTLEMENT || semantics.settlementTendency > 0.58) {
      placeSettlementMarker(world, chunkPos, semantics, interpretation.substrate);
    }
    if (intent == IntentType.EXPAND_TERRITORY || semantics.expansionTendency > 0.55) {
      placeFrontierMarker(world, chunkPos, semantics);
    }
    if (intent == IntentType.COLLAPSE || semantics.collapseTendency > 0.72) {
      markAbandonment(world, chunkPos);
    }
  }

  private void placeSettlementMarker(ServerWorld world, ChunkPos chunkPos, SubstrateSemantics semantics,
                                     com.utdmod.tension.SubstrateState substrate) {
    int x = chunkPos.getStartX() + 6 + (int) (semantics.localDensity * 4);
    int z = chunkPos.getStartZ() + 6 + (int) (semantics.prosperityPotential * 4);
    BlockPos surface = findSurface(world, x, z);
    if (surface == null) {
      return;
    }
    Block below = world.getBlockState(surface.down()).getBlock();
    if (below == Blocks.GRASS_BLOCK || below == Blocks.DIRT) {
      if (semantics.prosperityPotential > 0.6) {
        world.setBlockState(surface, Blocks.HAY_BLOCK.getDefaultState());
      } else {
        world.setBlockState(surface, Blocks.CAMPFIRE.getDefaultState());
      }
      ExperimentLogger.logActuatorDecision(world.getTime(), world, chunkPos, getId(),
          substrate, "SETTLEMENT_MARKER", "settlement tendency + density",
          "place habitation marker", Blocks.CAMPFIRE.getTranslationKey(), surface, 0L,
          ActuatorOutcome.SUCCESS);
    }
  }

  private void placeFrontierMarker(ServerWorld world, ChunkPos chunkPos, SubstrateSemantics semantics) {
    var dir = SpatialBehaviorHelper.resolveCorridorDirection(world,
        InterpretationService.get(world, chunkPos));
    int biasX = dir != null ? dir.stepX() * 6 : (int) Math.round(semantics.transportBiasX * 6);
    int biasZ = dir != null ? dir.stepZ() * 6 : (int) Math.round(semantics.transportBiasZ * 6);
    int x = chunkPos.getStartX() + 8 + biasX;
    int z = chunkPos.getStartZ() + 8 + biasZ;
    BlockPos surface = findSurface(world, x, z);
    if (surface == null) {
      return;
    }
    if (world.getBlockState(surface).isAir()) {
      world.setBlockState(surface, Blocks.TORCH.getDefaultState());
    }
  }

  private void markAbandonment(ServerWorld world, ChunkPos chunkPos) {
    int x = chunkPos.getStartX() + 7;
    int z = chunkPos.getStartZ() + 7;
    BlockPos surface = findSurface(world, x, z);
    if (surface == null) {
      return;
    }
    if (world.getBlockState(surface).getBlock() == Blocks.CAMPFIRE
        || world.getBlockState(surface).getBlock() == Blocks.HAY_BLOCK) {
      world.setBlockState(surface, Blocks.DEAD_BUSH.getDefaultState());
    }
  }

  private BlockPos findSurface(ServerWorld world, int x, int z) {
    return SpatialBehaviorHelper.findSurface(world, x, z);
  }

  @Override
  public String getId() {
    return "settlement_structure";
  }
}
