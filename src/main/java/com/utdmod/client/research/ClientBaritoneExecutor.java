package com.utdmod.client.research;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.process.IBuilderProcess;
import baritone.api.process.ICustomGoalProcess;
import baritone.api.process.IExploreProcess;
import baritone.api.process.IMineProcess;
import baritone.api.schematic.AbstractSchematic;
import com.utdmod.research.agent.ResearchObjectiveType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Client-side Baritone actuator. The only place on the client that invokes Baritone processes.
 */
final class ClientBaritoneExecutor {
  private final MinecraftClient client;
  private String currentTask = "idle";
  private boolean buildingActive;
  private BlockPos shelterOrigin = BlockPos.ORIGIN;
  private BlockPos navTarget = BlockPos.ORIGIN;

  ClientBaritoneExecutor(MinecraftClient client) {
    this.client = client;
  }

  void applyObjective(
      ResearchObjectiveType type,
      BlockPos target,
      int centerX,
      int centerZ,
      String[] blockNames
  ) {
    IBaritone baritone = baritone();
    if (baritone == null) return;

    switch (type) {
      case STOP -> stopAll(baritone);
      case GO_TO -> {
        currentTask = "navigate";
        navTarget = target;
        baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(target));
      }
      case EXPLORE -> {
        currentTask = "explore";
        baritone.getExploreProcess().explore(centerX, centerZ);
      }
      case MINE -> {
        currentTask = "mine";
        IMineProcess mine = baritone.getMineProcess();
        if (blockNames != null && blockNames.length > 0) {
          mine.mineByName(blockNames);
        }
      }
      case BUILD_SHELTER -> {
        currentTask = "build";
        buildingActive = true;
        shelterOrigin = target;
        IBuilderProcess builder = baritone.getBuilderProcess();
        builder.build("research_shelter", new ShelterSchematic(), target);
      }
      case RETURN_HOME -> {
        currentTask = "returnHome";
        navTarget = target;
        baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(target));
      }
      case FACE -> {
        currentTask = "face";
        navTarget = target;
        baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(target));
      }
    }
  }

  void tick() {
    IBaritone baritone = baritone();
    if (baritone == null) return;
    if (isIdle(baritone)) {
      currentTask = "idle";
    }
  }

  boolean isAtTarget(double tolerance) {
    if (client.player == null || navTarget == BlockPos.ORIGIN) {
      return true;
    }
    BlockPos playerPos = client.player.getBlockPos();
    double dx = playerPos.getX() + 0.5 - (navTarget.getX() + 0.5);
    double dz = playerPos.getZ() + 0.5 - (navTarget.getZ() + 0.5);
    return Math.sqrt(dx * dx + dz * dz) <= tolerance;
  }

  boolean isIdle() {
    IBaritone baritone = baritone();
    return baritone == null || isIdle(baritone);
  }

  boolean isMining() {
    IBaritone baritone = baritone();
    return baritone != null && baritone.getMineProcess().isActive();
  }

  boolean isExploring() {
    IBaritone baritone = baritone();
    return baritone != null && baritone.getExploreProcess().isActive();
  }

  boolean isBuilding() {
    return buildingActive;
  }

  boolean isBuildComplete() {
    IBaritone baritone = baritone();
    if (!buildingActive || baritone == null) {
      return true;
    }
    return !baritone.getBuilderProcess().isActive();
  }

  String currentTask() {
    return currentTask;
  }

  private boolean isIdle(IBaritone baritone) {
    return !baritone.getCustomGoalProcess().isActive()
        && !baritone.getMineProcess().isActive()
        && !baritone.getExploreProcess().isActive()
        && !baritone.getBuilderProcess().isActive();
  }

  private void stopAll(IBaritone baritone) {
    baritone.getMineProcess().cancel();
    baritone.getExploreProcess().onLostControl();
    baritone.getBuilderProcess().onLostControl();
    baritone.getCustomGoalProcess().onLostControl();
    baritone.getPathingBehavior().cancelEverything();
    buildingActive = false;
    currentTask = "idle";
    navTarget = BlockPos.ORIGIN;
  }

  private IBaritone baritone() {
    try {
      return BaritoneAPI.getProvider().getPrimaryBaritone();
    } catch (Throwable t) {
      return null;
    }
  }

  private static final class ShelterSchematic extends AbstractSchematic {
    private static final int WIDTH = 5;
    private static final int DEPTH = 5;
    private static final int HEIGHT = 3;

    ShelterSchematic() {
      super(WIDTH, HEIGHT + 1, DEPTH);
    }

    @Override
    public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
      if (y == 0 || y == HEIGHT) {
        return Blocks.COBBLESTONE.getDefaultState();
      }
      boolean wall = x == 0 || x == WIDTH - 1 || z == 0 || z == DEPTH - 1;
      if (wall) {
        if (z == 0 && x == 2 && y == 1) {
          return Blocks.AIR.getDefaultState();
        }
        return Blocks.COBBLESTONE.getDefaultState();
      }
      if (x == 2 && y == 1 && z == 2) return Blocks.CRAFTING_TABLE.getDefaultState();
      if (x == 1 && y == 1 && z == 2) return Blocks.FURNACE.getDefaultState();
      if (x == 3 && y == 1 && z == 2) return Blocks.RED_BED.getDefaultState();
      if (x == 2 && y == 2 && z == 2) return Blocks.TORCH.getDefaultState();
      return Blocks.AIR.getDefaultState();
    }
  }
}
