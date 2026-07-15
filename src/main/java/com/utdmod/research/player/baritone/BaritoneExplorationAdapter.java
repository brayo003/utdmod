package com.utdmod.research.player.baritone;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.process.IExploreProcess;
import com.utdmod.research.player.ResearchPlayerSnapshot;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Translates exploration intents into Baritone ExploreProcess commands.
 * Retains deterministic spiral waypoints for radius-bounded fallback navigation.
 */
final class BaritoneExplorationAdapter {
  private final ServerWorld world;
  private final ResearchPlayerSnapshot snapshot;
  private boolean baritoneExploring;

  BaritoneExplorationAdapter(ServerWorld world, ResearchPlayerSnapshot snapshot) {
    this.world = world;
    this.snapshot = snapshot;
  }

  void explore(int centerX, int centerZ) {
    IBaritone baritone = resolveBaritone();
    if (baritone != null) {
      IExploreProcess explore = baritone.getExploreProcess();
      explore.explore(centerX, centerZ);
      baritoneExploring = true;
      return;
    }
    baritoneExploring = false;
  }

  BlockPos nextWaypoint() {
    int index = snapshot.explorationIndex;
    int layer = (int) Math.floor((Math.sqrt(index + 1) + 1) / 2);
    int legLen = layer * 2;
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

  void reset() {
    snapshot.explorationIndex = 0;
  }

  boolean isActive() {
    if (baritoneExploring) {
      IBaritone baritone = resolveBaritone();
      if (baritone != null && baritone.getExploreProcess().isActive()) {
        return true;
      }
    }
    return false;
  }

  void stop() {
    baritoneExploring = false;
    IBaritone baritone = resolveBaritone();
    if (baritone != null) {
      baritone.getExploreProcess().onLostControl();
    }
  }

  private static IBaritone resolveBaritone() {
    try {
      return BaritoneAPI.getProvider().getPrimaryBaritone();
    } catch (Throwable ignored) {
      return null;
    }
  }
}
