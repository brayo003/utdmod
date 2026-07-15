package com.utdmod.research.player.baritone;

import com.utdmod.research.player.ResearchPlayerInventoryManager;
import com.utdmod.research.player.ResearchPlayerSnapshot;
import net.minecraft.block.Block;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Execution backend facade: routes experiment intents to Baritone process adapters.
 * This is the only public class in this package; all Baritone API access stays here.
 */
public final class BaritoneResearchController {
  private final BaritoneNavigationAdapter navigation;
  private final BaritoneMiningAdapter mining;
  private final BaritoneExplorationAdapter exploration;
  private final BaritoneBuildingAdapter building;
  private String currentTask = "idle";
  private boolean buildingActive;

  public BaritoneResearchController(
      ServerWorld world,
      ServerPlayerEntity player,
      ResearchPlayerSnapshot snapshot
  ) {
    this.navigation = new BaritoneNavigationAdapter(world, player);
    this.mining = new BaritoneMiningAdapter(world, player);
    this.exploration = new BaritoneExplorationAdapter(world, snapshot);
    this.building = new BaritoneBuildingAdapter(world, player, snapshot);
  }

  public void goTo(BlockPos target) {
    currentTask = "goTo";
    navigation.setTarget(target);
  }

  public void setTarget(BlockPos target) {
    currentTask = "navigate";
    navigation.setTarget(target);
  }

  public BlockPos getTarget() {
    return navigation.getTarget();
  }

  public boolean tick() {
    if (mining.isBreaking()) {
      mining.tickServerBreak(navigation);
      currentTask = "mining";
      return false;
    }
    if (buildingActive && !building.isComplete()) {
      building.tickBuildStep(navigation);
      currentTask = "building";
      return building.isComplete();
    }
    navigation.tick();
    if (isIdle()) {
      currentTask = "idle";
    }
    return navigation.isAtTarget(2.5);
  }

  public boolean isAtTarget(double tolerance) {
    return navigation.isAtTarget(tolerance);
  }

  public void face(BlockPos pos) {
    navigation.face(pos);
  }

  public void mine(Block... blocks) {
    currentTask = "mine";
    mining.mineBlocks(blocks);
  }

  public void mineByName(String... blockNames) {
    currentTask = "mine";
    mining.mineByName(blockNames);
  }

  public void startServerMining(BlockPos pos, ResearchPlayerInventoryManager inventory) {
    currentTask = "mine";
    mining.startServerBreak(pos, navigation, inventory);
  }

  public boolean isMining() {
    return mining.isBreaking();
  }

  public void stopMining() {
    mining.stop();
    if (isIdle()) {
      currentTask = "idle";
    }
  }

  public void explore(int centerX, int centerZ) {
    currentTask = "explore";
    exploration.explore(centerX, centerZ);
  }

  public BlockPos nextExplorationWaypoint() {
    return exploration.nextWaypoint();
  }

  public void resetExploration() {
    exploration.reset();
  }

  public boolean isExploring() {
    return exploration.isActive();
  }

  public void setShelterOrigin(BlockPos origin) {
    currentTask = "build";
    buildingActive = true;
    building.setShelterOrigin(origin);
  }

  public boolean tickBuildStep() {
    currentTask = "build";
    buildingActive = true;
    boolean done = building.tickBuildStep(navigation);
    if (done) {
      buildingActive = false;
    }
    return done;
  }

  public boolean isBuildComplete() {
    return !buildingActive || building.isComplete();
  }

  public void stop() {
    mining.stop();
    exploration.stop();
    building.stop();
    navigation.stop();
    buildingActive = false;
    currentTask = "idle";
  }

  public boolean isIdle() {
    return !navigation.isActive()
        && !mining.isBreaking()
        && !exploration.isActive()
        && !buildingActive;
  }

  public String currentTask() {
    return currentTask;
  }
}
