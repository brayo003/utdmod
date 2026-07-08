package com.utdmod.research.player;

import com.utdmod.UTDMod;
import com.utdmod.research.ResearchConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Lifecycle manager for the deterministic research survival player.
 * ResearchBotManager delegates here — no direct perturbation injection.
 */
public final class ResearchPlayerController {
  private static final ResearchPlayerFsm FSM = new ResearchPlayerFsm();
  // Multi-agent: one context per research-player UUID.
  private static final java.util.Map<java.util.UUID, ResearchPlayerContext> ACTIVE_CONTEXTS =
      new java.util.HashMap<>();

  private ResearchPlayerController() {}

  public static void tick(MinecraftServer server, long tick) {
    if (!ResearchConfig.isBotEnabled()) {
      return;
    }
    ServerWorld world = server.getOverworld();
    boolean anyRunning = false;
    for (ServerPlayerEntity player : ResearchPlayerRegistry.getAll()) {
      if (player.getServerWorld() != world || !player.isAlive()) {
        continue;
      }
      java.util.UUID uuid = player.getUuid();
      ResearchPlayerPersistence persistence = ResearchPlayerPersistence.get(world, uuid);
      ResearchPlayerSnapshot snapshot = persistence.snapshot();
      if (!snapshot.running || snapshot.paused) {
        continue;
      }
      anyRunning = true;

      ResearchPlayerContext ctx = ACTIVE_CONTEXTS.get(uuid);
      if (ctx == null || ctx.player != player) {
        ctx = new ResearchPlayerContext(world, player, snapshot);
        ACTIVE_CONTEXTS.put(uuid, ctx);
      }

      player.playerTick();
      FSM.tick(ctx, tick);
      persistence.saveSnapshot(snapshot);
    }

    if (!anyRunning) {
      ResearchConfig.setBotEnabled(false);
    }
  }

  public static boolean spawn(ServerWorld world, int botSeed) {
    BlockPos spawnPos = ResearchPlayerEntityFactory.defaultSpawn(world, botSeed);
    ServerPlayerEntity player = ResearchPlayerEntityFactory.spawn(world, botSeed, spawnPos);
    if (player == null) {
      return false;
    }

    ResearchPlayerPersistence persistence = ResearchPlayerPersistence.get(world, player.getUuid());
    ResearchPlayerSnapshot snapshot = persistence.snapshot();
    snapshot.agentId = ResearchPlayerEntityFactory.DEFAULT_AGENT_ID;
    snapshot.botSeed = botSeed;
    snapshot.spawned = true;
    snapshot.state = ResearchPlayerState.INITIALIZE;
    snapshot.previousState = ResearchPlayerState.IDLE;
    snapshot.transitionReason = "spawn";
    snapshot.stateTicks = 0;
    snapshot.running = true;
    snapshot.paused = false;
    snapshot.homePos = spawnPos.toImmutable();
    snapshot.protocolId = snapshot.protocolId == null ? "PASSIVE_WANDER" : snapshot.protocolId;
    snapshot.experimentId = snapshot.experimentId == null ? "default_experiment" : snapshot.experimentId;

    persistence.saveSnapshot(snapshot);

    ResearchPlayerContext ctx = new ResearchPlayerContext(world, player, snapshot);
    ACTIVE_CONTEXTS.put(player.getUuid(), ctx);

    ResearchPlayerLogger.initialize(botSeed);
    ResearchPlayerLogger.setEnabled(snapshot.loggingEnabled);
    return true;
  }

  public static void remove(ServerWorld world) {
    ResearchPlayerEntityFactory.remove(world);
    ServerPlayerEntity player = ResearchPlayerRegistry.get(world);
    if (player != null) {
      ResearchPlayerPersistence persistence = ResearchPlayerPersistence.get(world, player.getUuid());
      ResearchPlayerSnapshot snapshot = persistence.snapshot();
      snapshot.spawned = false;
      snapshot.running = false;
      persistence.saveSnapshot(snapshot);
      ACTIVE_CONTEXTS.remove(player.getUuid());
    }
    ResearchPlayerLogger.close();
  }

  public static void start(ServerWorld world) {
    ServerPlayerEntity player = ResearchPlayerRegistry.get(world);
    if (player == null) {
      return;
    }
    ResearchPlayerPersistence persistence = ResearchPlayerPersistence.get(world, player.getUuid());
    ResearchPlayerSnapshot snapshot = persistence.snapshot();
    snapshot.running = true;
    snapshot.paused = false;
    snapshot.stateTicks = 0;
    if (!snapshot.spawned) {
      spawn(world, snapshot.botSeed != 0 ? snapshot.botSeed : ResearchConfig.getBotSeed());
    } else if (snapshot.state == ResearchPlayerState.IDLE) {
      snapshot.state = ResearchPlayerState.RESUME_PREVIOUS_TASK;
    }
    persistence.saveSnapshot(snapshot);
    ResearchConfig.setBotEnabled(true);
  }

  public static void stop(ServerWorld world) {
    ServerPlayerEntity player = ResearchPlayerRegistry.get(world);
    if (player == null) {
      return;
    }
    ResearchPlayerPersistence persistence = ResearchPlayerPersistence.get(world, player.getUuid());
    ResearchPlayerSnapshot snapshot = persistence.snapshot();
    snapshot.running = false;
    persistence.saveSnapshot(snapshot);
    ResearchConfig.setBotEnabled(false);
  }

  public static void pause(ServerWorld world) {
    ServerPlayerEntity player = ResearchPlayerRegistry.get(world);
    if (player == null) return;
    ResearchPlayerPersistence persistence = ResearchPlayerPersistence.get(world, player.getUuid());
    persistence.snapshot().paused = true;
    persistence.saveSnapshot(persistence.snapshot());
  }

  public static void resume(ServerWorld world) {
    ServerPlayerEntity player = ResearchPlayerRegistry.get(world);
    if (player == null) return;
    ResearchPlayerSnapshot snapshot = ResearchPlayerPersistence.get(world, player.getUuid()).snapshot();
    snapshot.paused = false;
    snapshot.running = true;
    ResearchPlayerPersistence.get(world, player.getUuid()).saveSnapshot(snapshot);
  }

  public static void reset(ServerWorld world, int botSeed) {
    remove(world);
    ResearchConfig.setBotSeed(botSeed);
    spawn(world, botSeed);
    start(world);
  }

  public static void gotoState(ServerWorld world, ResearchPlayerState state) {
    if (state == null) {
      return;
    }
    ServerPlayerEntity player = ResearchPlayerRegistry.get(world);
    if (player == null) return;
    ResearchPlayerSnapshot snapshot = ResearchPlayerPersistence.get(world, player.getUuid()).snapshot();
    snapshot.previousState = snapshot.state;
    snapshot.state = state;
    snapshot.transitionReason = "command goto";
    snapshot.stateTicks = 0;
    ResearchPlayerPersistence.get(world, player.getUuid()).saveSnapshot(snapshot);
  }

  public static void setHome(ServerWorld world, BlockPos pos) {
    ServerPlayerEntity player = ResearchPlayerRegistry.get(world);
    if (player == null) return;
    ResearchPlayerSnapshot snapshot = ResearchPlayerPersistence.get(world, player.getUuid()).snapshot();
    snapshot.homePos = pos.toImmutable();
    ResearchPlayerPersistence.get(world, player.getUuid()).saveSnapshot(snapshot);
  }

  public static ResearchPlayerSnapshot status(ServerWorld world) {
    ServerPlayerEntity player = ResearchPlayerRegistry.get(world);
    if (player == null) {
      return new ResearchPlayerSnapshot();
    }
    return ResearchPlayerPersistence.get(world, player.getUuid()).snapshot();
  }

  public static ServerPlayerEntity getPlayer(ServerWorld world) {
    return ResearchPlayerRegistry.get(world);
  }
}
