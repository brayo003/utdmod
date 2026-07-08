package com.utdmod.research.player;

import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
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

/** Fixed 5x5x3 shelter with single north entrance — identical on every run. */
public final class ResearchPlayerBuilding {
  private static final int WIDTH = 5;
  private static final int DEPTH = 5;
  private static final int HEIGHT = 3;

  private final ServerWorld world;
  private final ServerPlayerEntity player;
  private final ResearchPlayerSnapshot snapshot;

  public ResearchPlayerBuilding(ServerWorld world, ServerPlayerEntity player, ResearchPlayerSnapshot snapshot) {
    this.world = world;
    this.player = player;
    this.snapshot = snapshot;
  }

  public void setShelterOrigin(BlockPos origin) {
    snapshot.shelterOrigin = origin.toImmutable();
    snapshot.shelterStep = 0;
  }

  public boolean tickBuildStep(ResearchPlayerNavigation navigation) {
    if (snapshot.shelterOrigin == BlockPos.ORIGIN) {
      BlockPos base = player.getBlockPos();
      setShelterOrigin(new BlockPos(base.getX(), base.getY(), base.getZ()));
    }
    BlockPos[] plan = buildPlan(snapshot.shelterOrigin);
    if (snapshot.shelterStep >= plan.length) {
      placeInterior();
      return true;
    }
    BlockPos target = plan[snapshot.shelterStep];
    navigation.setTarget(target);
    if (!navigation.isAtTarget(3.0)) {
      return false;
    }
    navigation.face(target);
    if (!placeBlock(target, Blocks.COBBLESTONE.getDefaultState())) {
      snapshot.shelterStep++;
      return false;
    }
    snapshot.shelterStep++;
    return snapshot.shelterStep >= plan.length;
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
    // Floor
    for (int x = 0; x < WIDTH; x++) {
      for (int z = 0; z < DEPTH; z++) {
        positions.add(o.add(x, 0, z));
      }
    }
    // Walls
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
    // Roof
    for (int x = 0; x < WIDTH; x++) {
      for (int z = 0; z < DEPTH; z++) {
        positions.add(o.add(x, HEIGHT, z));
      }
    }
  // North entrance gap at z=0, x=2
    return positions.toArray(new BlockPos[0]);
  }

  private boolean placeBlock(BlockPos pos, net.minecraft.block.BlockState state) {
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

  private int findItemStack(ItemStack needle) {
    for (int i = 0; i < 9; i++) {
      if (player.getInventory().getStack(i).isOf(needle.getItem())) {
        return i;
      }
    }
    return -1;
  }

  private int findItemSlot(net.minecraft.item.Item item) {
    for (int i = 0; i < 9; i++) {
      if (player.getInventory().getStack(i).isOf(item)) {
        return i;
      }
    }
    return -1;
  }
}
