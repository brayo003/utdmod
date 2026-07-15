package com.utdmod.research.player;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Spawn helpers for research client agents.
 * Agents are real connected players — not server-side FakePlayers.
 */
public final class ResearchPlayerEntityFactory {
  public static final String PLAYER_NAME = "UTDResearchBot";
  public static final String DEFAULT_AGENT_ID = "agent0";

  private ResearchPlayerEntityFactory() {}

  public static BlockPos defaultSpawn(ServerWorld world, int botSeed) {
    BlockPos spawn = world.getSpawnPos();
    int surfaceY = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
    DeterministicRandom rng = DeterministicRandom.fromSeeds(world.getSeed(), botSeed);
    int dx = rng.nextInt(5) - 2;
    int dz = rng.nextInt(5) - 2;
    return new BlockPos(spawn.getX() + dx, Math.max(surfaceY, 64), spawn.getZ() + dz);
  }

  public static double horizontalDistance(ServerPlayerEntity player, BlockPos pos) {
    Vec3d p = player.getPos();
    double dx = p.x - (pos.getX() + 0.5);
    double dz = p.z - (pos.getZ() + 0.5);
    return Math.sqrt(dx * dx + dz * dz);
  }
}
