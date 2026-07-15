package com.utdmod.research.agent;

import com.utdmod.research.player.ResearchExplorationWaypoints;
import com.utdmod.research.player.ResearchPlayerEntityFactory;
import com.utdmod.research.player.ResearchPlayerInventoryManager;
import com.utdmod.research.player.ResearchPlayerSnapshot;
import net.minecraft.block.Block;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Server-side execution facade. Issues high-level objectives to the linked research
 * client and reads back reported status. Performs no navigation, mining, or pathing.
 */
public final class RemoteResearchController implements ResearchAgentController {
  private final ServerWorld world;
  private final ServerPlayerEntity player;
  private final ResearchPlayerSnapshot snapshot;
  private BlockPos target = BlockPos.ORIGIN;
  private String currentTask = "idle";
  private boolean buildingActive;
  private long lastObjectiveId = -1;

  public RemoteResearchController(ServerWorld world, ServerPlayerEntity player, ResearchPlayerSnapshot snapshot) {
    this.world = world;
    this.player = player;
    this.snapshot = snapshot;
  }

  private void dispatch(ResearchObjectiveType type, BlockPos pos, int centerX, int centerZ, String... blockNames) {
    if (!ResearchClientAgentManager.isClientConnected(player)) {
      return;
    }
    lastObjectiveId = ResearchClientAgentManager.dispatchObjective(
        player, snapshot.agentId, type, pos, centerX, centerZ, blockNames
    );
  }

  private ResearchClientAgentStatus status() {
    return ResearchClientAgentManager.getStatus(snapshot.agentId);
  }

  @Override
  public void goTo(BlockPos target) {
    currentTask = "goTo";
    this.target = target == null ? BlockPos.ORIGIN : target.toImmutable();
    dispatch(ResearchObjectiveType.GO_TO, this.target, 0, 0);
  }

  @Override
  public void setTarget(BlockPos target) {
    currentTask = "navigate";
    this.target = target == null ? BlockPos.ORIGIN : target.toImmutable();
    dispatch(ResearchObjectiveType.GO_TO, this.target, 0, 0);
  }

  @Override
  public BlockPos getTarget() {
    return target;
  }

  @Override
  public boolean tick() {
    ResearchClientAgentStatus s = status();
    if (s != null) {
      currentTask = s.currentTask;
    }
    return isAtTarget(2.5);
  }

  @Override
  public boolean isAtTarget(double tolerance) {
    ResearchClientAgentStatus s = status();
    if (s != null && s.lastObjectiveId >= lastObjectiveId) {
      return s.atTarget;
    }
    if (target == BlockPos.ORIGIN) {
      return true;
    }
    return ResearchPlayerEntityFactory.horizontalDistance(player, target) <= tolerance;
  }

  @Override
  public void face(BlockPos pos) {
    dispatch(ResearchObjectiveType.FACE, pos, 0, 0);
  }

  @Override
  public void mine(Block... blocks) {
    currentTask = "mine";
    String[] names = new String[blocks.length];
    for (int i = 0; i < blocks.length; i++) {
      names[i] = net.minecraft.registry.Registries.BLOCK.getId(blocks[i]).toString();
    }
    dispatch(ResearchObjectiveType.MINE, BlockPos.ORIGIN, 0, 0, names);
  }

  @Override
  public void mineByName(String... blockNames) {
    currentTask = "mine";
    dispatch(ResearchObjectiveType.MINE, BlockPos.ORIGIN, 0, 0, blockNames);
  }

  @Override
  public void startServerMining(BlockPos pos, ResearchPlayerInventoryManager inventory) {
    currentTask = "mine";
    dispatch(ResearchObjectiveType.GO_TO, pos, 0, 0);
  }

  @Override
  public boolean isMining() {
    ResearchClientAgentStatus s = status();
    return s != null && s.mining;
  }

  @Override
  public void stopMining() {
    dispatch(ResearchObjectiveType.STOP, BlockPos.ORIGIN, 0, 0);
    currentTask = "idle";
  }

  @Override
  public void explore(int centerX, int centerZ) {
    currentTask = "explore";
    dispatch(ResearchObjectiveType.EXPLORE, BlockPos.ORIGIN, centerX, centerZ);
  }

  @Override
  public BlockPos nextExplorationWaypoint() {
    return ResearchExplorationWaypoints.nextWaypoint(world, snapshot);
  }

  @Override
  public void resetExploration() {
    ResearchExplorationWaypoints.reset(snapshot);
  }

  @Override
  public boolean isExploring() {
    ResearchClientAgentStatus s = status();
    return s != null && s.exploring;
  }

  @Override
  public void setShelterOrigin(BlockPos origin) {
    currentTask = "build";
    buildingActive = true;
    dispatch(ResearchObjectiveType.BUILD_SHELTER, origin, 0, 0);
  }

  @Override
  public boolean tickBuildStep() {
    currentTask = "build";
    buildingActive = true;
    ResearchClientAgentStatus s = status();
    if (s != null && s.buildComplete) {
      buildingActive = false;
      return true;
    }
    return false;
  }

  @Override
  public boolean isBuildComplete() {
    if (!buildingActive) {
      return true;
    }
    ResearchClientAgentStatus s = status();
    return s != null && s.buildComplete;
  }

  @Override
  public void stop() {
    dispatch(ResearchObjectiveType.STOP, BlockPos.ORIGIN, 0, 0);
    buildingActive = false;
    currentTask = "idle";
    target = BlockPos.ORIGIN;
  }

  @Override
  public boolean isIdle() {
    ResearchClientAgentStatus s = status();
    if (s != null) {
      return s.idle;
    }
    return !buildingActive;
  }

  @Override
  public String currentTask() {
    ResearchClientAgentStatus s = status();
    return s != null && s.currentTask != null ? s.currentTask : currentTask;
  }

  public void returnHome() {
    currentTask = "returnHome";
    dispatch(ResearchObjectiveType.RETURN_HOME, snapshot.homePos, 0, 0);
  }
}
