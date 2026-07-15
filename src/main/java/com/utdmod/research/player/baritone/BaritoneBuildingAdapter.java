package com.utdmod.research.player.baritone;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.process.IBuilderProcess;
import baritone.api.schematic.AbstractSchematic;
import baritone.api.schematic.ISchematic;
import com.utdmod.research.player.ResearchPlayerSnapshot;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Translates build intents into Baritone BuilderProcess commands.
 * Preserves the deterministic 5x5x3 shelter plan for server-side fallback.
 */
final class BaritoneBuildingAdapter {
  private static final int WIDTH = 5;
  private static final int DEPTH = 5;
  private static final int HEIGHT = 3;

  private final ServerWorld world;
  private final ServerPlayerEntity player;
  private final ResearchPlayerSnapshot snapshot;
  private boolean baritoneBuilding;

  BaritoneBuildingAdapter(ServerWorld world, ServerPlayerEntity player, ResearchPlayerSnapshot snapshot) {
    this.world = world;
    this.player = player;
    this.snapshot = snapshot;
  }

  void setShelterOrigin(BlockPos origin) {
    snapshot.shelterOrigin = origin.toImmutable();
    snapshot.shelterStep = 0;
    IBaritone baritone = resolveBaritone();
    if (baritone != null) {
      IBuilderProcess builder = baritone.getBuilderProcess();
      ShelterSchematic schematic = new ShelterSchematic();
      builder.build("research_shelter", schematic, origin);
      baritoneBuilding = true;
    } else {
      baritoneBuilding = false;
    }
  }

  boolean tickBuildStep(BaritoneNavigationAdapter navigation) {
    IBaritone baritone = resolveBaritone();
    if (baritone != null && baritoneBuilding && baritone.getBuilderProcess().isActive()) {
      navigation.tick();
      return !baritone.getBuilderProcess().isActive();
    }
    return tickServerBuildStep(navigation);
  }

  boolean isComplete() {
    if (baritoneBuilding) {
      IBaritone baritone = resolveBaritone();
      if (baritone != null) {
        return !baritone.getBuilderProcess().isActive();
      }
    }
    BlockPos[] plan = buildPlan(snapshot.shelterOrigin);
    return snapshot.shelterStep >= plan.length;
  }

  void stop() {
    baritoneBuilding = false;
    IBaritone baritone = resolveBaritone();
    if (baritone != null) {
      baritone.getBuilderProcess().onLostControl();
    }
  }

  private boolean tickServerBuildStep(BaritoneNavigationAdapter navigation) {
    if (snapshot.shelterOrigin == BlockPos.ORIGIN) {
      BlockPos base = player.getBlockPos();
      snapshot.shelterOrigin = new BlockPos(base.getX(), base.getY(), base.getZ());
      snapshot.shelterStep = 0;
    }
    BlockPos[] plan = buildPlan(snapshot.shelterOrigin);
    if (snapshot.shelterStep >= plan.length) {
      placeInterior();
      return true;
    }
    BlockPos target = plan[snapshot.shelterStep];
    navigation.setTarget(target);
    if (!navigation.isAtTarget(3.0)) {
      navigation.tick();
      return false;
    }
    navigation.face(target);
    if (!placeBlock(target, Blocks.COBBLESTONE.getDefaultState())) {
      snapshot.shelterStep++;
      return false;
    }
    snapshot.shelterStep++;
    if (snapshot.shelterStep >= plan.length) {
      placeInterior();
      return true;
    }
    return false;
  }

  private void placeInterior() {
    BlockPos o = snapshot.shelterOrigin;
    placeBlock(o.add(2, 1, 2), Blocks.CRAFTING_TABLE.getDefaultState());
    placeBlock(o.add(1, 1, 2), Blocks.FURNACE.getDefaultState());
    placeBlock(o.add(3, 1, 2), Blocks.RED_BED.getDefaultState());
    placeBlock(o.add(2, 2, 2), Blocks.TORCH.getDefaultState());
  }

  private BlockPos[] buildPlan(BlockPos o) {
    java.util.List<BlockPos> positions = new java.util.ArrayList<>();
    for (int x = 0; x < WIDTH; x++) {
      for (int z = 0; z < DEPTH; z++) {
        positions.add(o.add(x, 0, z));
      }
    }
    for (int y = 1; y < HEIGHT; y++) {
      for (int x = 0; x < WIDTH; x++) {
        positions.add(o.add(x, y, 0));
        positions.add(o.add(x, y, DEPTH - 1));
      }
      for (int z = 1; z < DEPTH - 1; z++) {
        positions.add(o.add(0, y, z));
        positions.add(o.add(WIDTH - 1, y, z));
      }
    }
    for (int x = 0; x < WIDTH; x++) {
      for (int z = 0; z < DEPTH; z++) {
        positions.add(o.add(x, HEIGHT, z));
      }
    }
    return positions.toArray(new BlockPos[0]);
  }

  private boolean placeBlock(BlockPos pos, BlockState state) {
    if (!world.isChunkLoaded(pos)) {
      return false;
    }
    if (!world.getBlockState(pos).isReplaceable()) {
      return true;
    }
    int slot = findItemSlot(Items.COBBLESTONE);
    if (slot < 0) {
      return false;
    }
    player.getInventory().selectedSlot = slot;
    Vec3d hit = Vec3d.ofCenter(pos.down());
    BlockHitResult bhr = new BlockHitResult(hit, Direction.UP, pos.down(), false);
    ItemStack stack = player.getInventory().getStack(slot);
    ActionResult result = player.interactionManager.interactBlock(player, world, stack, Hand.MAIN_HAND, bhr);
    if (result.isAccepted()) {
      return true;
    }
    return world.setBlockState(pos, state);
  }

  private int findItemSlot(net.minecraft.item.Item item) {
    for (int i = 0; i < 9; i++) {
      if (player.getInventory().getStack(i).isOf(item)) {
        return i;
      }
    }
    return -1;
  }

  private static IBaritone resolveBaritone() {
    try {
      return BaritoneAPI.getProvider().getPrimaryBaritone();
    } catch (Throwable ignored) {
      return null;
    }
  }

  /** Baritone schematic for the deterministic research shelter. */
  private static final class ShelterSchematic extends AbstractSchematic {
    ShelterSchematic() {
      super(WIDTH, HEIGHT + 1, DEPTH);
    }

    @Override
    public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
      if (y == 0) {
        return Blocks.COBBLESTONE.getDefaultState();
      }
      if (y == HEIGHT) {
        return Blocks.COBBLESTONE.getDefaultState();
      }
      boolean wall = x == 0 || x == WIDTH - 1 || z == 0 || z == DEPTH - 1;
      if (wall) {
        if (z == 0 && x == 2 && y == 1) {
          return Blocks.AIR.getDefaultState();
        }
        return Blocks.COBBLESTONE.getDefaultState();
      }
      if (x == 2 && y == 1 && z == 2) {
        return Blocks.CRAFTING_TABLE.getDefaultState();
      }
      if (x == 1 && y == 1 && z == 2) {
        return Blocks.FURNACE.getDefaultState();
      }
      if (x == 3 && y == 1 && z == 2) {
        return Blocks.RED_BED.getDefaultState();
      }
      if (x == 2 && y == 2 && z == 2) {
        return Blocks.TORCH.getDefaultState();
      }
      return Blocks.AIR.getDefaultState();
    }
  }
}
