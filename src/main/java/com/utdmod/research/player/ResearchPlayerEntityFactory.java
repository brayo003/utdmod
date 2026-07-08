package com.utdmod.research.player;

import com.mojang.authlib.GameProfile;
import com.utdmod.UTDMod;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class ResearchPlayerEntityFactory {
  public static final String PLAYER_NAME = "UTDResearchBot";
  public static final String DEFAULT_AGENT_ID = "agent0";

  private ResearchPlayerEntityFactory() {}

  public static ServerPlayerEntity spawn(ServerWorld world, int botSeed, BlockPos spawnPos) {
    return spawn(world, DEFAULT_AGENT_ID, botSeed, spawnPos);
  }

  public static ServerPlayerEntity spawn(ServerWorld world, String agentId, int botSeed, BlockPos spawnPos) {
    if (agentId == null || agentId.isBlank()) {
      agentId = DEFAULT_AGENT_ID;
    }
    UUID uuid = UUID.nameUUIDFromBytes(
        (PLAYER_NAME + ":" + agentId + ":" + botSeed).getBytes(StandardCharsets.UTF_8));
    GameProfile profile = new GameProfile(uuid, PLAYER_NAME + ":" + agentId);

    // FakePlayer.get() with a stable GameProfile yields a stable entity identity for the session.

    // Creates a server-controlled player backed by Fabric's network handler infrastructure
    ServerPlayerEntity player = FakePlayer.get(world, profile);
    
    player.refreshPositionAndAngles(
        spawnPos.getX() + 0.5,
        spawnPos.getY(),
        spawnPos.getZ() + 0.5,
        0.0f,
        0.0f);
    player.setHealth(player.getMaxHealth());
    player.getHungerManager().setFoodLevel(20);
    player.getHungerManager().setSaturationLevel(20.0f);

    // Fabric's FakePlayer handles its own internal world registration inside .get(). 
    // We verify viability and register it down into your existing system.
    if (!player.isAlive()) {
      UTDMod.LOGGER.error("[UTD] Failed to spawn research player entity");
      return null;
    }

    ResearchPlayerRegistry.register(player, agentId);
    UTDMod.LOGGER.info("[UTD] Spawned research player {}:{} at {}", PLAYER_NAME, agentId, spawnPos);
    return player;
  }

  public static void remove(ServerWorld world) {
    remove(world, DEFAULT_AGENT_ID);
  }

  public static void remove(ServerWorld world, String agentId) {
    if (agentId == null || agentId.isBlank()) agentId = DEFAULT_AGENT_ID;
    for (ServerPlayerEntity player : ResearchPlayerRegistry.getAll()) {
      if (!player.isAlive() || player.getServerWorld() != world) continue;
      String currentAgent = ResearchPlayerRegistry.getAgentId(player);
      if (agentId.equals(currentAgent)) {
        ResearchPlayerRegistry.unregister(player);
        player.discard();
        UTDMod.LOGGER.info("[UTD] Removed research player {}:{}", PLAYER_NAME, agentId);
        return;
      }
    }
  }

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
