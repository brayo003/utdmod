package com.utdmod.interpretation;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

/**
 * Shared spatial interpretation utilities for actuators.
 * Reads neighbor {@link ChunkInterpretation} snapshots — never substrate mathematics.
 */
public final class SpatialBehaviorHelper {
  private static final int[][] CARDINAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

  private SpatialBehaviorHelper() {}

  /** Resolved horizontal corridor step from local + neighbor semantics. */
  public record CorridorDirection(int stepX, int stepZ, double strength) {}

  public static boolean isChunkLoaded(ServerWorld world, int blockX, int blockZ) {
    return world != null && world.isChunkLoaded(blockX >> 4, blockZ >> 4);
  }

  public static boolean isChunkLoaded(ServerWorld world, ChunkPos pos) {
    return world != null && pos != null && world.isChunkLoaded(pos.x, pos.z);
  }

  /**
   * Combines local transport bias with the strongest loaded neighbor corridor,
   * producing a direction that can span multiple chunks when trade/road intents align.
   */
  public static CorridorDirection resolveCorridorDirection(ServerWorld world, ChunkInterpretation self) {
    if (self == null || self.semantics == null) {
      return new CorridorDirection(1, 0, 0.0);
    }
    SubstrateSemantics semantics = self.semantics;
    double biasX = semantics.transportBiasX;
    double biasZ = semantics.transportBiasZ;
    double strength = semantics.corridorStrength;

    ChunkPos origin = self.chunkPos;
    for (int[] dir : CARDINAL) {
      ChunkPos neighborPos = new ChunkPos(origin.x + dir[0], origin.z + dir[1]);
      if (!isChunkLoaded(world, neighborPos)) {
        continue;
      }
      ChunkInterpretation neighbor = InterpretationService.get(world, neighborPos);
      if (neighbor == null || neighbor.semantics == null) {
        continue;
      }
      double neighborStrength = neighbor.semantics.corridorStrength;
      boolean tradeLink = semantics.tradeTendency > 0.35 && neighbor.semantics.tradeTendency > 0.35;
      boolean roadLink = neighborStrength > InterpretationThresholds.TRANSPORT_CORRIDOR
          || neighbor.primaryIntent != null && (
          neighbor.primaryIntent.type == IntentType.CREATE_ROAD
              || neighbor.primaryIntent.type == IntentType.INITIATE_TRADE);
      if (neighborStrength > strength * 0.75 || tradeLink || roadLink) {
        biasX += dir[0] * neighborStrength;
        biasZ += dir[1] * neighborStrength;
        strength = Math.max(strength, neighborStrength);
      }
    }

    int stepX = (int) Math.signum(biasX);
    int stepZ = (int) Math.signum(biasZ);
    if (stepX == 0 && stepZ == 0) {
      stepX = self.stencil.activationLaplacian >= 0 ? 1 : -1;
    }
    return new CorridorDirection(stepX, stepZ, strength);
  }

  public static Vec3d corridorTarget(ServerWorld world, Vec3d from, ChunkInterpretation self, double distance) {
    if (from == null || self == null) {
      return Vec3d.ZERO;
    }
    CorridorDirection dir = resolveCorridorDirection(world, self);
    double mag = Math.hypot(dir.stepX, dir.stepZ);
    if (mag < 1e-6) {
      return from;
    }
    return new Vec3d(
        from.x + (dir.stepX / mag) * distance,
        from.y,
        from.z + (dir.stepZ / mag) * distance);
  }

  public static Vec3d fleeFromCorridor(Vec3d from, ChunkInterpretation self, double distance) {
    if (from == null || self == null || self.semantics == null) {
      return from != null ? from : Vec3d.ZERO;
    }
    return new Vec3d(
        from.x - self.semantics.transportBiasX * distance,
        from.y,
        from.z - self.semantics.transportBiasZ * distance);
  }

  /**
   * Wears grass into paths along a corridor, continuing into loaded neighbor chunks.
   */
  public static int wearCorridorSegment(ServerWorld world, ChunkPos chunkPos, ChunkInterpretation interpretation,
                                        int maxSteps) {
    if (!isChunkLoaded(world, chunkPos) || interpretation == null) {
      return 0;
    }
    CorridorDirection dir = resolveCorridorDirection(world, interpretation);
    int startX = chunkPos.getStartX() + 8;
    int startZ = chunkPos.getStartZ() + 8;
    int placed = 0;
    for (int step = 0; step < maxSteps; step++) {
      int x = startX + dir.stepX * step * 2;
      int z = startZ + dir.stepZ * step * 2;
      if (!isChunkLoaded(world, x, z)) {
        break;
      }
      if (wearPathAt(world, x, z)) {
        placed++;
      }
    }
    return placed;
  }

  public static boolean wearPathAt(ServerWorld world, int x, int z) {
    if (!isChunkLoaded(world, x, z)) {
      return false;
    }
    for (int y = 62; y < 120; y++) {
      BlockPos pos = new BlockPos(x, y, z);
      Block block = world.getBlockState(pos).getBlock();
      if (block == Blocks.GRASS_BLOCK) {
        world.setBlockState(pos, Blocks.DIRT_PATH.getDefaultState());
        return true;
      }
      if (block == Blocks.DIRT) {
        world.setBlockState(pos, Blocks.COARSE_DIRT.getDefaultState());
        return true;
      }
    }
    return false;
  }

  public static BlockPos findSurface(ServerWorld world, int x, int z) {
    if (!isChunkLoaded(world, x, z)) {
      return null;
    }
    for (int y = world.getTopY(); y > 48; y--) {
      BlockPos pos = new BlockPos(x, y, z);
      if (world.getBlockState(pos).isAir() && !world.getBlockState(pos.down()).isAir()) {
        return pos;
      }
    }
    return null;
  }
}
