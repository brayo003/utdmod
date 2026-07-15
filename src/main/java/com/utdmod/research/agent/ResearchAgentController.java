package com.utdmod.research.agent;

import com.utdmod.research.player.ResearchPlayerInventoryManager;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;

/**
 * Experiment execution facade. Server implementations dispatch objectives to
 * research clients; client implementations delegate to Baritone.
 */
public interface ResearchAgentController {

  void goTo(BlockPos target);

  void setTarget(BlockPos target);

  BlockPos getTarget();

  boolean tick();

  boolean isAtTarget(double tolerance);

  void face(BlockPos pos);

  void mine(Block... blocks);

  void mineByName(String... blockNames);

  void startServerMining(BlockPos pos, ResearchPlayerInventoryManager inventory);

  boolean isMining();

  void stopMining();

  void explore(int centerX, int centerZ);

  BlockPos nextExplorationWaypoint();

  void resetExploration();

  boolean isExploring();

  void setShelterOrigin(BlockPos origin);

  boolean tickBuildStep();

  boolean isBuildComplete();

  void stop();

  boolean isIdle();

  String currentTask();
}
