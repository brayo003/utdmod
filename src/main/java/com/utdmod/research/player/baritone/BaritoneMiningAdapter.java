package com.utdmod.research.player.baritone;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.process.IMineProcess;
import com.utdmod.research.player.ResearchPlayerInventoryManager;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Translates mining intents into Baritone MineProcess commands.
 * Falls back to server-side block breaking when Baritone is unavailable.
 */
final class BaritoneMiningAdapter {
  private final ServerWorld world;
  private final ServerPlayerEntity player;
  private BlockPos breakingPos = BlockPos.ORIGIN;
  private int breakTicks;
  private boolean baritoneMining;

  BaritoneMiningAdapter(ServerWorld world, ServerPlayerEntity player) {
    this.world = world;
    this.player = player;
  }

  void mineBlocks(Block... blocks) {
    IBaritone baritone = resolveBaritone();
    if (baritone != null) {
      IMineProcess mine = baritone.getMineProcess();
      mine.mine(blocks);
      baritoneMining = true;
      return;
    }
    baritoneMining = false;
  }

  void mineByName(String... blockNames) {
    IBaritone baritone = resolveBaritone();
    if (baritone != null) {
      baritone.getMineProcess().mineByName(blockNames);
      baritoneMining = true;
      return;
    }
    baritoneMining = false;
  }

  void startServerBreak(BlockPos pos, BaritoneNavigationAdapter navigation, ResearchPlayerInventoryManager inventory) {
    breakingPos = pos;
    breakTicks = 0;
    baritoneMining = false;
    navigation.face(pos);
    inventory.selectBestTool(net.minecraft.item.Items.WOODEN_PICKAXE);
  }

  boolean tickServerBreak(BaritoneNavigationAdapter navigation) {
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

  boolean isBreaking() {
    if (breakingPos != BlockPos.ORIGIN) {
      return true;
    }
    IBaritone baritone = resolveBaritone();
    return baritone != null && baritone.getMineProcess().isActive();
  }

  boolean isBaritoneMining() {
    return baritoneMining;
  }

  void stop() {
    breakingPos = BlockPos.ORIGIN;
    breakTicks = 0;
    baritoneMining = false;
    IBaritone baritone = resolveBaritone();
    if (baritone != null) {
      baritone.getMineProcess().cancel();
    }
  }

  static Block[] coalBlocks() {
    return new Block[] {Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE};
  }

  static Block[] ironBlocks() {
    return new Block[] {Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE};
  }

  private static IBaritone resolveBaritone() {
    try {
      return BaritoneAPI.getProvider().getPrimaryBaritone();
    } catch (Throwable ignored) {
      return null;
    }
  }
}
