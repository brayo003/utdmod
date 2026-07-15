package com.utdmod.research.agent;

import com.utdmod.UTDMod;
import com.utdmod.network.ResearchAgentObjectivePacket;
import com.utdmod.research.experiment.ResearchExperimentConfig;
import com.utdmod.research.player.ResearchPlayerPersistence;
import com.utdmod.research.player.ResearchPlayerRegistry;
import com.utdmod.research.player.ResearchPlayerSnapshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side registry for research client agents.
 * Links agentId slots to connected players and caches client execution status.
 */
public final class ResearchClientAgentManager {
  private static final Map<String, PendingAgent> PENDING = new ConcurrentHashMap<>();
  private static final Map<String, ResearchClientAgentStatus> STATUS = new ConcurrentHashMap<>();
  /** Exclusive owner: agentId → player UUID. */
  private static final Map<String, UUID> AGENT_OWNER = new ConcurrentHashMap<>();
  /** Reverse lookup: player UUID → agentId. */
  private static final Map<UUID, String> PLAYER_AGENT = new ConcurrentHashMap<>();
  private static long nextObjectiveId = 1;

  private ResearchClientAgentManager() {}

  public static void reserveAgent(ServerWorld world, String agentId, ResearchExperimentConfig cfg, BlockPos spawnPos) {
    if (agentId == null || agentId.isBlank()) agentId = "agent0";
    PENDING.put(agentId, new PendingAgent(world, cfg, spawnPos));
  }

  public static void onClientRegister(
      MinecraftServer server,
      ServerPlayerEntity player,
      String agentId,
      String experimentId,
      int botSeed
  ) {
    if (agentId == null || agentId.isBlank()) agentId = "agent0";
    if (experimentId == null || experimentId.isBlank()) experimentId = "default_experiment";
    ServerWorld world = player.getServerWorld();

    PendingAgent pending = PENDING.get(agentId);
    if (pending == null || pending.world != world) {
      UTDMod.LOGGER.warn("[UTD] Rejected agent registration for {}: no pending reservation", agentId);
      return;
    }

    if (!experimentId.equals(pending.cfg.experimentId) || botSeed != pending.cfg.botSeed) {
      UTDMod.LOGGER.warn(
          "[UTD] Rejected agent registration for {}: identity mismatch (expected experimentId={}, botSeed={}; got experimentId={}, botSeed={})",
          agentId, pending.cfg.experimentId, pending.cfg.botSeed, experimentId, botSeed
      );
      return;
    }

    UUID existingOwner = AGENT_OWNER.get(agentId);
    if (existingOwner != null && !existingOwner.equals(player.getUuid())) {
      UTDMod.LOGGER.warn("[UTD] Rejected agent registration for {}: already owned by {}", agentId, existingOwner);
      return;
    }

    ResearchPlayerPersistence persistence = ResearchPlayerPersistence.get(world, player.getUuid());
    ResearchPlayerSnapshot snapshot = persistence.snapshot();
    boolean reconnect = snapshot.spawned
        && agentId.equals(snapshot.agentId)
        && experimentId.equals(snapshot.experimentId)
        && botSeed == snapshot.botSeed;

    AGENT_OWNER.put(agentId, player.getUuid());
    PLAYER_AGENT.put(player.getUuid(), agentId);
    ResearchPlayerRegistry.register(player, agentId);

    if (reconnect) {
      syncSnapshotFromPending(world, agentId, player, pending, false);
      UTDMod.LOGGER.info("[UTD] Research client agent reconnected: {} as {}", player.getName().getString(), agentId);
    } else {
      applyPendingToPlayer(world, agentId, player, pending);
      UTDMod.LOGGER.info("[UTD] Research client agent registered: {} as {}", player.getName().getString(), agentId);
    }

    PENDING.remove(agentId);
    STATUS.remove(agentId);
  }

  public static void onClientStatus(
      ServerPlayerEntity player,
      String agentId,
      long objectiveId,
      BlockPos pos,
      boolean atTarget,
      boolean idle,
      boolean mining,
      boolean exploring,
      boolean building,
      boolean buildComplete,
      String currentTask
  ) {
    if (agentId == null || agentId.isBlank()) agentId = "agent0";

    UUID owner = AGENT_OWNER.get(agentId);
    if (owner == null || !owner.equals(player.getUuid())) {
      return;
    }

    ResearchClientAgentStatus status = STATUS.computeIfAbsent(agentId, k -> new ResearchClientAgentStatus());
    status.lastObjectiveId = objectiveId;
    status.position = pos == null ? BlockPos.ORIGIN : pos.toImmutable();
    status.atTarget = atTarget;
    status.idle = idle;
    status.mining = mining;
    status.exploring = exploring;
    status.building = building;
    status.buildComplete = buildComplete;
    status.currentTask = currentTask == null ? "idle" : currentTask;
    status.lastReportTick = player.getServer().getTicks();
  }

  public static void onPlayerDisconnect(ServerPlayerEntity player) {
    if (player == null) return;
    String agentId = PLAYER_AGENT.remove(player.getUuid());
    ResearchPlayerRegistry.unregister(player);
    if (agentId == null) {
      return;
    }

    AGENT_OWNER.remove(agentId);
    STATUS.remove(agentId);

    ServerWorld world = player.getServerWorld();
    restorePendingReservation(world, agentId, player);

    UTDMod.LOGGER.info("[UTD] Research client agent disconnected: {}", agentId);
  }

  public static boolean isClientConnected(ServerPlayerEntity player) {
    if (player == null) {
      return false;
    }
    String agentId = PLAYER_AGENT.get(player.getUuid());
    if (agentId == null) {
      return false;
    }
    UUID owner = AGENT_OWNER.get(agentId);
    return player.getUuid().equals(owner);
  }

  public static ResearchClientAgentStatus getStatus(String agentId) {
    return STATUS.get(agentId);
  }

  public static long dispatchObjective(
      ServerPlayerEntity player,
      String agentId,
      ResearchObjectiveType type,
      BlockPos target,
      int centerX,
      int centerZ,
      String[] blockNames
  ) {
    if (player == null || !isClientConnected(player)) return -1;
    UUID owner = AGENT_OWNER.get(agentId);
    if (owner == null || !owner.equals(player.getUuid())) return -1;
    long id = nextObjectiveId++;
    ResearchAgentObjectivePacket.send(player, id, agentId, type, target, centerX, centerZ, blockNames);
    return id;
  }

  public static ServerPlayerEntity findLinkedPlayer(ServerWorld world, String agentId) {
    if (agentId == null || agentId.isBlank()) {
      return null;
    }
    UUID owner = AGENT_OWNER.get(agentId);
    if (owner == null) {
      return null;
    }
    ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(owner);
    if (player == null || player.getServerWorld() != world || !player.isAlive()) {
      return null;
    }
    return player;
  }

  public static boolean hasLinkedPlayer(ServerWorld world, String agentId) {
    return findLinkedPlayer(world, agentId) != null;
  }

  private static void applyPendingToPlayer(
      ServerWorld world,
      String agentId,
      ServerPlayerEntity player,
      PendingAgent pending
  ) {
    ResearchExperimentConfig cfg = pending.cfg;
    BlockPos start = pending.spawnPos;

    player.teleport(world, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, player.getYaw(), player.getPitch());

    ResearchPlayerPersistence persistence = ResearchPlayerPersistence.get(world, player.getUuid());
    ResearchPlayerSnapshot snapshot = persistence.snapshot();
    writeSnapshotFromConfig(snapshot, agentId, cfg, start);
    snapshot.stateTicks = 0;
    snapshot.running = true;
    snapshot.paused = false;
    snapshot.spawned = true;
    snapshot.transitionReason = "client agent linked";
    persistence.saveSnapshot(snapshot);
  }

  private static void syncSnapshotFromPending(
      ServerWorld world,
      String agentId,
      ServerPlayerEntity player,
      PendingAgent pending,
      boolean resetProgress
  ) {
    ResearchPlayerPersistence persistence = ResearchPlayerPersistence.get(world, player.getUuid());
    ResearchPlayerSnapshot snapshot = persistence.snapshot();
    BlockPos anchor = snapshot.homePos != BlockPos.ORIGIN ? snapshot.homePos : pending.spawnPos;
    writeSnapshotFromConfig(snapshot, agentId, pending.cfg, anchor);
    snapshot.spawned = true;
    snapshot.transitionReason = "client agent reconnected";
    if (resetProgress) {
      snapshot.stateTicks = 0;
    }
    persistence.saveSnapshot(snapshot);
  }

  private static void writeSnapshotFromConfig(
      ResearchPlayerSnapshot snapshot,
      String agentId,
      ResearchExperimentConfig cfg,
      BlockPos homePos
  ) {
    snapshot.agentId = agentId;
    snapshot.botSeed = cfg.botSeed;
    snapshot.experimentId = cfg.experimentId;
    snapshot.protocolId = cfg.protocolId;
    snapshot.previousProtocolId = cfg.protocolId;
    snapshot.loggingLabel = cfg.loggingLabel;
    snapshot.allowedRadiusChunks = cfg.allowedRadiusChunks;
    snapshot.durationTicks = cfg.durationTicks;
    snapshot.experimentSeed = cfg.experimentSeed;
    snapshot.miningOreId = cfg.miningOreId;
    snapshot.miningGoalCount = cfg.miningGoalCount;
    if (homePos != null && homePos != BlockPos.ORIGIN) {
      snapshot.homePos = homePos.toImmutable();
    }
  }

  private static void restorePendingReservation(ServerWorld world, String agentId, ServerPlayerEntity player) {
    ResearchPlayerPersistence persistence = ResearchPlayerPersistence.get(world, player.getUuid());
    ResearchPlayerSnapshot snapshot = persistence.snapshot();
    if (!snapshot.running) {
      return;
    }

    BlockPos spawnPos = snapshot.homePos != BlockPos.ORIGIN ? snapshot.homePos : player.getBlockPos();
    ResearchExperimentConfig cfg = ResearchExperimentConfig.fromSnapshot(snapshot, spawnPos);
    cfg.botSeed = snapshot.botSeed;
    cfg.experimentId = snapshot.experimentId;
    PENDING.put(agentId, new PendingAgent(world, cfg, spawnPos));
  }

  private record PendingAgent(ServerWorld world, ResearchExperimentConfig cfg, BlockPos spawnPos) {}
}
