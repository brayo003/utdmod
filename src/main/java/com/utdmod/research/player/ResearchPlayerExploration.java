package com.utdmod.research.player;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Deterministic spiral exploration from home. */
public final class ResearchPlayerExploration {
  private final ServerWorld world;
  private final ResearchPlayerSnapshot snapshot;

  public ResearchPlayerExploration(ServerWorld world, ResearchPlayerSnapshot snapshot) {
    this.world = world;
    this.snapshot = snapshot;
  }

  public BlockPos nextWaypoint() {
    int index = snapshot.explorationIndex;
    int layer = (int) Math.floor((Math.sqrt(index + 1) + 1) / 2);
    int legLen = layer * 2;
    int leg = index == 0 ? 0 : (index - (2 * layer - 1) * (2 * layer - 1)) / (legLen > 0 ? legLen : 1);
    int dir = (index / Math.max(1, legLen)) % 4;
    int x = snapshot.homePos.getX();
    int z = snapshot.homePos.getZ();
    switch (dir % 4) {
      case 0 -> x += layer * 16;
      case 1 -> z += layer * 16;
      case 2 -> x -= layer * 16;
      default -> z -= layer * 16;
    }
    int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
    snapshot.explorationIndex++;
    return new BlockPos(x, Math.max(y, 64), z);
  }

  public void reset() {
    snapshot.explorationIndex = 0;
  }
}
