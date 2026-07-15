package com.utdmod.research.player;

import com.utdmod.research.experiment.ResearchExperimentConfig;
import com.utdmod.research.experiment.ResearchExperimentManager;
import com.utdmod.UTDMod;
import com.utdmod.research.ResearchConfig;
import com.utdmod.research.agent.ResearchClientAgentManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Lifecycle manager for research client agents.
 * ResearchBotManager delegates here — no direct perturbation injection.
 */
public final class ResearchPlayerController {
  private static final ResearchPlayerFsm FSM = new ResearchPlayerFsm();
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
      if (!ResearchClientAgentManager.isClientConnected(player)) {
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

      FSM.tick(ctx, tick);
      persistence.saveSnapshot(snapshot);
    }

    if (!anyRunning) {
      ResearchConfig.setBotEnabled(false);
    }
  }

  public static boolean spawn(ServerWorld world, int botSeed) {
    return spawn(world, ResearchPlayerEntityFactory.DEFAULT_AGENT_ID, botSeed,
        ResearchPlayerEntityFactory.defaultSpawn(world, botSeed));
  }

  public static boolean spawn(ServerWorld world, String agentId, int botSeed, BlockPos spawnPos) {
    ResearchPlayerSnapshot template = new ResearchPlayerSnapshot();
    template.agentId = agentId;
    template.botSeed = botSeed;
    template.spawned = true;
    template.state = ResearchPlayerState.INITIALIZE;
    template.previousState = ResearchPlayerState.IDLE;
    template.transitionReason = "spawn";
    template.stateTicks = 0;
    template.running = true;
    template.paused = false;
    template.homePos = spawnPos.toImmutable();
    template.protocolId = template.protocolId == null ? "PASSIVE_WANDER" : template.protocolId;
    template.experimentId = template.experimentId == null ? "default_experiment" : template.experimentId;

    ResearchExperimentConfig cfg = ResearchExperimentConfig.fromSnapshot(template, spawnPos);
    ResearchExperimentManager.reserveClientAgent(world, agentId, cfg, spawnPos);

    ServerPlayerEntity player = ResearchClientAgentManager.findLinkedPlayer(world, agentId);
    if (player != null) {
      ResearchPlayerContext ctx = new ResearchPlayerContext(world, player, ResearchPlayerPersistence.get(world, player.getUuid()).snapshot());
      ACTIVE_CONTEXTS.put(player.getUuid(), ctx);
    }

    ResearchPlayerLogger.initialize(botSeed);
    ResearchPlayerLogger.setEnabled(template.loggingEnabled);
    ResearchConfig.setBotEnabled(true);
    UTDMod.LOGGER.info("[UTD] Reserved research client agent slot {} (awaiting client connection)", agentId);
    return true;
  }

  public static void remove(ServerWorld world) {
    remove(world, ResearchPlayerEntityFactory.DEFAULT_AGENT_ID);
  }

  public static void remove(ServerWorld world, String agentId) {
    ServerPlayerEntity player = ResearchClientAgentManager.findLinkedPlayer(world, agentId);
    if (player != null) {
      ResearchPlayerPersistence persistence = ResearchPlayerPersistence.get(world, player.getUuid());
      ResearchPlayerSnapshot snapshot = persistence.snapshot();
      snapshot.spawned = false;
      snapshot.running = false;
      persistence.saveSnapshot(snapshot);
      ACTIVE_CONTEXTS.remove(player.getUuid());
      ResearchPlayerRegistry.unregister(player);
    }
    ResearchPlayerLogger.close();
  }

  public static void start(ServerWorld world) {
    ServerPlayerEntity player = ResearchPlayerRegistry.get(world);
    if (player == null) {
      ResearchConfig.setBotEnabled(true);
      return;
    }
    ResearchPlayerPersistence persistence = ResearchPlayerPersistence.get(world, player.getUuid());
    ResearchPlayerSnapshot snapshot = persistence.snapshot();
    snapshot.running = true;
    snapshot.paused = false;
    snapshot.stateTicks = 0;
    if (snapshot.state == ResearchPlayerState.IDLE) {
      snapshot.state = ResearchPlayerState.RESUME_PREVIOUS_TASK;
    }
    persistence.saveSnapshot(snapshot);
    ResearchConfig.setBotEnabled(true);
  }

  public static void stop(ServerWorld world) {
    ServerPlayerEntity player = ResearchPlayerRegistry.get(world);
    if (player == null) {
      ResearchConfig.setBotEnabled(false);
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
