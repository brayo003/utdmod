package com.utdmod.research.player;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks active research player entities for movement + event integration. */
public final class ResearchPlayerRegistry {
  private static final Map<UUID, ServerPlayerEntity> ACTIVE = new ConcurrentHashMap<>();
  private static final Map<UUID, String> AGENT_ID_BY_UUID = new ConcurrentHashMap<>();

  private ResearchPlayerRegistry() {}

  public static void register(ServerPlayerEntity player, String agentId) {
    if (player != null) {
      ACTIVE.put(player.getUuid(), player);
      AGENT_ID_BY_UUID.put(player.getUuid(), agentId);
    }
  }

  public static void register(ServerPlayerEntity player) {
    register(player, "agent0");
  }

  public static void unregister(ServerPlayerEntity player) {
    if (player != null) {
      ACTIVE.remove(player.getUuid());
      AGENT_ID_BY_UUID.remove(player.getUuid());
    }
  }

  public static ServerPlayerEntity get(ServerWorld world) {
    for (ServerPlayerEntity player : ACTIVE.values()) {
      if (player.getServerWorld() == world && player.isAlive()) {
        return player;
      }
    }
    return null;
  }

  public static String getAgentId(ServerPlayerEntity player) {
    if (player == null) return "agent0";
    return AGENT_ID_BY_UUID.getOrDefault(player.getUuid(), "agent0");
  }

  public static String getAgentId(UUID uuid) {
    return AGENT_ID_BY_UUID.getOrDefault(uuid, "agent0");
  }

  public static List<ServerPlayerEntity> getAll() {
    return Collections.unmodifiableList(new ArrayList<>(ACTIVE.values()));
  }

  public static void clear() {
    ACTIVE.clear();
  }
}
