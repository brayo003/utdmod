package com.utdmod.research.player;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic block/entity scans sorted by distance then coordinate. */
public final class ResearchPlayerVision {
  private static final int SCAN_RADIUS = 8;
  private final ServerWorld world;
  private final ServerPlayerEntity player;

  public ResearchPlayerVision(ServerWorld world, ServerPlayerEntity player) {
    this.world = world;
    this.player = player;
  }

  public BlockPos findNearestLog() {
    return findNearestMatching(this::isLog);
  }

  public BlockPos findNearestStone() {
    return findNearestMatching(state -> state.isOf(Blocks.STONE) || state.isOf(Blocks.COBBLESTONE));
  }

  public BlockPos findNearestCoal() {
    return findNearestMatching(state -> state.isOf(Blocks.COAL_ORE) || state.isOf(Blocks.DEEPSLATE_COAL_ORE));
  }

  public BlockPos findNearestIron() {
    return findNearestMatching(state -> state.isOf(Blocks.IRON_ORE) || state.isOf(Blocks.DEEPSLATE_IRON_ORE));
  }

  public BlockPos findNearestWater() {
    return findNearestMatching(state -> !state.getFluidState().isEmpty());
  }

  public BlockPos findNearestCraftingTable() {
    return findNearestMatching(state -> state.isOf(Blocks.CRAFTING_TABLE));
  }

  public BlockPos findNearestBed() {
    return findNearestMatching(state -> state.isIn(BlockTags.BEDS));
  }

  public BlockPos findNearestFurnace() {
    return findNearestMatching(state -> state.isOf(Blocks.FURNACE) || state.isOf(Blocks.BLAST_FURNACE));
  }

  public HostileEntity findNearestHostile() {
    Box box = player.getBoundingBox().expand(SCAN_RADIUS);
    List<HostileEntity> hostiles = world.getEntitiesByClass(HostileEntity.class, box, Entity::isAlive);
    hostiles.sort(Comparator
        .comparingDouble((HostileEntity e) -> e.squaredDistanceTo(player))
        .thenComparing(e -> e.getUuid().toString()));
    return hostiles.isEmpty() ? null : hostiles.get(0);
  }

  public AnimalEntity findNearestPassiveFood() {
    Box box = player.getBoundingBox().expand(SCAN_RADIUS);
    List<AnimalEntity> animals = world.getEntitiesByClass(AnimalEntity.class, box, Entity::isAlive);
    animals.sort(Comparator
        .comparingDouble((AnimalEntity e) -> e.squaredDistanceTo(player))
        .thenComparing(e -> e.getUuid().toString()));
    return animals.isEmpty() ? null : animals.get(0);
  }

  public BlockPos findSurfaceAt(int x, int z) {
    int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
    return new BlockPos(x, y, z);
  }

  private boolean isLog(BlockState state) {
    return state.isIn(BlockTags.LOGS);
  }

  private BlockPos findNearestMatching(java.util.function.Predicate<BlockState> predicate) {
    BlockPos origin = player.getBlockPos();
    List<BlockPos> matches = new ArrayList<>();
    for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
      for (int dy = -4; dy <= 4; dy++) {
        for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
          BlockPos pos = origin.add(dx, dy, dz);
          if (!world.isChunkLoaded(pos)) {
            continue;
          }
          if (predicate.test(world.getBlockState(pos))) {
            matches.add(pos.toImmutable());
          }
        }
      }
    }
    matches.sort(Comparator
        .comparingDouble((BlockPos pos) -> pos.getSquaredDistance(origin))
        .thenComparing(pos -> pos.getX())
        .thenComparing(pos -> pos.getZ())
        .thenComparing(pos -> pos.getY()));
    return matches.isEmpty() ? null : matches.get(0);
  }
}
