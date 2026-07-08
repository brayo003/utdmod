package com.utdmod.research.player;

import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Vanilla block breaking via ServerPlayerInteractionManager. */
public final class ResearchPlayerMining {
  private final ServerWorld world;
  private final ServerPlayerEntity player;
  private BlockPos breakingPos = BlockPos.ORIGIN;
  private int breakTicks;

  public ResearchPlayerMining(ServerWorld world, ServerPlayerEntity player) {
    this.world = world;
    this.player = player;
  }

  public void start(BlockPos pos, ResearchPlayerNavigation navigation, ResearchPlayerInventoryManager inventory) {
    breakingPos = pos;
    breakTicks = 0;
    navigation.face(pos);
    inventory.selectBestTool(net.minecraft.item.Items.WOODEN_PICKAXE);
  }

  public boolean tick(ResearchPlayerNavigation navigation) {
    if (breakingPos == BlockPos.ORIGIN) {
      return true;
    }
    if (!world.isChunkLoaded(breakingPos) || world.getBlockState(breakingPos).isAir()) {
      breakingPos = BlockPos.ORIGIN;
      return true;
    }
    navigation.face(breakingPos);
    Direction face = navigation.faceDirection(player.getBlockPos(), breakingPos);
    player.interactionManager.processBlockBreakingAction(
        breakingPos,
        PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
        face,
        world.getTopY(),
        breakTicks);
    player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
    breakTicks++;
    if (player.interactionManager.tryBreakBlock(breakingPos) || world.getBlockState(breakingPos).isAir()) {
      breakingPos = BlockPos.ORIGIN;
      return true;
    }
    return breakTicks > 200;
  }

  public boolean isBreaking() {
    return breakingPos != BlockPos.ORIGIN;
  }

  public void stop() {
    breakingPos = BlockPos.ORIGIN;
    breakTicks = 0;
  }
}
