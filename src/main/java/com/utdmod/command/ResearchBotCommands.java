package com.utdmod.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.utdmod.research.ResearchConfig;
import com.utdmod.research.player.ResearchPlayerController;
import com.utdmod.research.player.ResearchPlayerEntityFactory;
import com.utdmod.research.player.ResearchPlayerLogger;
import com.utdmod.research.player.ResearchPlayerRegistry;
import com.utdmod.research.player.ResearchPlayerSnapshot;
import com.utdmod.research.player.ResearchPlayerState;
import com.utdmod.research.experiment.ResearchExperimentConfig;
import com.utdmod.research.experiment.ResearchExperimentManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class ResearchBotCommands {

  private ResearchBotCommands() {}

  public static void register() {
    CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) ->
        dispatcher.register(CommandManager.literal("utdbot")
            .requires(s -> s.hasPermissionLevel(2))
            .then(CommandManager.literal("spawn").executes(ResearchBotCommands::spawn))
            .then(CommandManager.literal("remove").executes(ResearchBotCommands::remove))
            .then(CommandManager.literal("start").executes(ResearchBotCommands::start))
            .then(CommandManager.literal("stop").executes(ResearchBotCommands::stop))
            .then(CommandManager.literal("pause").executes(ResearchBotCommands::pause))
            .then(CommandManager.literal("resume").executes(ResearchBotCommands::resume))
            .then(CommandManager.literal("reset").executes(ResearchBotCommands::reset))
            .then(CommandManager.literal("status").executes(ResearchBotCommands::status))
            .then(CommandManager.literal("agent")
                .then(CommandManager.literal("spawn")
                    .then(CommandManager.argument("agentId", StringArgumentType.word())
                        .then(CommandManager.argument("botSeed", IntegerArgumentType.integer())
                            .executes(ResearchBotCommands::agentSpawn))))
                .then(CommandManager.literal("spawn_at")
                    .then(CommandManager.argument("agentId", StringArgumentType.word())
                        .then(CommandManager.argument("botSeed", IntegerArgumentType.integer())
                            .then(CommandManager.argument("startX", IntegerArgumentType.integer())
                                .then(CommandManager.argument("startY", IntegerArgumentType.integer())
                                    .then(CommandManager.argument("startZ", IntegerArgumentType.integer())
                                        .executes(ResearchBotCommands::agentSpawnAt)))))))
                .then(CommandManager.literal("stop")
                    .then(CommandManager.argument("agentId", StringArgumentType.word())
                        .executes(ResearchBotCommands::agentStop)))
                .then(CommandManager.literal("status")
                    .then(CommandManager.argument("agentId", StringArgumentType.word())
                        .executes(ResearchBotCommands::agentStatus))))
            .then(CommandManager.literal("exp")
                .then(CommandManager.literal("assign")
                    .then(CommandManager.argument("agentId", StringArgumentType.word())
                        .then(CommandManager.argument("protocolId", StringArgumentType.word())
                            .then(CommandManager.argument("durationTicks", IntegerArgumentType.integer())
                                .then(CommandManager.argument("allowedRadiusChunks", IntegerArgumentType.integer())
                                    .then(CommandManager.argument("fieldEnabled", BoolArgumentType.bool())
                                        .then(CommandManager.argument("couplingPresetId", StringArgumentType.word())
                                            .then(CommandManager.argument("experimentSeed", IntegerArgumentType.integer())
                                                .then(CommandManager.argument("loggingLabel", StringArgumentType.word())
                                                    .executes(ResearchBotCommands::expAssignGeneric))))))))))
                .then(CommandManager.literal("mine_coal")
                    .then(CommandManager.argument("agentId", StringArgumentType.word())
                        .then(CommandManager.argument("durationTicks", IntegerArgumentType.integer())
                            .then(CommandManager.argument("allowedRadiusChunks", IntegerArgumentType.integer())
                                .then(CommandManager.argument("goalCount", IntegerArgumentType.integer())
                                    .then(CommandManager.argument("experimentSeed", IntegerArgumentType.integer())
                                        .then(CommandManager.argument("loggingLabel", StringArgumentType.word())
                                            .executes(ResearchBotCommands::expMineCoal))))))))
                .then(CommandManager.literal("mine_iron")
                    .then(CommandManager.argument("agentId", StringArgumentType.word())
                        .then(CommandManager.argument("durationTicks", IntegerArgumentType.integer())
                            .then(CommandManager.argument("allowedRadiusChunks", IntegerArgumentType.integer())
                                .then(CommandManager.argument("goalCount", IntegerArgumentType.integer())
                                    .then(CommandManager.argument("experimentSeed", IntegerArgumentType.integer())
                                        .then(CommandManager.argument("loggingLabel", StringArgumentType.word())
                                            .executes(ResearchBotCommands::expMineIron))))))))
                .then(CommandManager.literal("explore")
                    .then(CommandManager.argument("agentId", StringArgumentType.word())
                        .then(CommandManager.argument("durationTicks", IntegerArgumentType.integer())
                            .then(CommandManager.argument("allowedRadiusChunks", IntegerArgumentType.integer())
                                .then(CommandManager.argument("experimentSeed", IntegerArgumentType.integer())
                                    .then(CommandManager.argument("loggingLabel", StringArgumentType.word())
                                        .executes(ResearchBotCommands::expExplore)))))))
                .then(CommandManager.literal("wander")
                    .then(CommandManager.argument("agentId", StringArgumentType.word())
                        .then(CommandManager.argument("durationTicks", IntegerArgumentType.integer())
                            .then(CommandManager.argument("allowedRadiusChunks", IntegerArgumentType.integer())
                                .then(CommandManager.argument("experimentSeed", IntegerArgumentType.integer())
                                    .then(CommandManager.argument("loggingLabel", StringArgumentType.word())
                                        .executes(ResearchBotCommands::expWander)))))))
                .then(CommandManager.literal("build_shelter")
                    .then(CommandManager.argument("agentId", StringArgumentType.word())
                        .then(CommandManager.argument("durationTicks", IntegerArgumentType.integer())
                            .then(CommandManager.argument("allowedRadiusChunks", IntegerArgumentType.integer())
                                .then(CommandManager.argument("experimentSeed", IntegerArgumentType.integer())
                                    .then(CommandManager.argument("loggingLabel", StringArgumentType.word())
                                        .executes(ResearchBotCommands::expBuildShelter)))))))
                .then(CommandManager.literal("combat")
                    .then(CommandManager.argument("agentId", StringArgumentType.word())
                        .then(CommandManager.argument("durationTicks", IntegerArgumentType.integer())
                            .then(CommandManager.argument("allowedRadiusChunks", IntegerArgumentType.integer())
                                .then(CommandManager.argument("experimentSeed", IntegerArgumentType.integer())
                                    .then(CommandManager.argument("loggingLabel", StringArgumentType.word())
                                        .executes(ResearchBotCommands::expCombat))))))))
                .then(CommandManager.literal("farmer")
                    .then(CommandManager.argument("agentId", StringArgumentType.word())
                        .then(CommandManager.argument("durationTicks", IntegerArgumentType.integer())
                            .then(CommandManager.argument("allowedRadiusChunks", IntegerArgumentType.integer())
                                .then(CommandManager.argument("experimentSeed", IntegerArgumentType.integer())
                                    .then(CommandManager.argument("loggingLabel", StringArgumentType.word())
                                        .executes(ResearchBotCommands::expFarmer)))))))
                .then(CommandManager.literal("trade")
                    .then(CommandManager.argument("agentId", StringArgumentType.word())
                        .then(CommandManager.argument("durationTicks", IntegerArgumentType.integer())
                            .then(CommandManager.argument("allowedRadiusChunks", IntegerArgumentType.integer())
                                .then(CommandManager.argument("experimentSeed", IntegerArgumentType.integer())
                                    .then(CommandManager.argument("loggingLabel", StringArgumentType.word())
                                        .executes(ResearchBotCommands::expTrade)))))))
                .then(CommandManager.literal("resource_transport")
                    .then(CommandManager.argument("agentId", StringArgumentType.word())
                        .then(CommandManager.argument("durationTicks", IntegerArgumentType.integer())
                            .then(CommandManager.argument("allowedRadiusChunks", IntegerArgumentType.integer())
                                .then(CommandManager.argument("experimentSeed", IntegerArgumentType.integer())
                                    .then(CommandManager.argument("loggingLabel", StringArgumentType.word())
                                        .executes(ResearchBotCommands::expTransport)))))))
            .then(CommandManager.literal("seed")
                .then(CommandManager.argument("seed", IntegerArgumentType.integer())
                    .executes(ResearchBotCommands::seed)))
            .then(CommandManager.literal("goto")
                .then(CommandManager.argument("state", StringArgumentType.word())
                    .executes(ResearchBotCommands::gotoState)))
            .then(CommandManager.literal("home").executes(ResearchBotCommands::home))
            .then(CommandManager.literal("log")
                .then(CommandManager.literal("on").executes(ResearchBotCommands::logOn))
                .then(CommandManager.literal("off").executes(ResearchBotCommands::logOff)))));
  }

  private static ServerWorld world(CommandContext<ServerCommandSource> ctx) {
    return ctx.getSource().getWorld();
  }

  private static int spawn(CommandContext<ServerCommandSource> ctx) {
    ServerWorld world = world(ctx);
    int seed = ResearchConfig.getBotSeed();
    boolean ok = ResearchPlayerController.spawn(world, seed);
    if (!ok) {
      ctx.getSource().sendError(Text.literal("Failed to spawn research player."));
      return 0;
    }
    ctx.getSource().sendFeedback(() -> Text.literal(
        "Spawned research player " + ResearchPlayerEntityFactory.PLAYER_NAME + " seed=" + seed), true);
    return 1;
  }

  private static int agentSpawn(CommandContext<ServerCommandSource> ctx) {
    ServerWorld world = world(ctx);
    String agentId = StringArgumentType.getString(ctx, "agentId");
    int botSeed = IntegerArgumentType.getInteger(ctx, "botSeed");

    ResearchExperimentConfig cfg = new ResearchExperimentConfig();
    cfg.protocolId = "PASSIVE_WANDER";
    cfg.durationTicks = 2000;
    cfg.allowedRadiusChunks = 8;
    cfg.botSeed = botSeed;
    cfg.experimentSeed = botSeed;
    cfg.experimentId = "exp_" + agentId;
    cfg.loggingLabel = "spawn";

    ResearchExperimentManager.spawnAgent(world, agentId, cfg);
    ctx.getSource().sendFeedback(() -> Text.literal("Spawned agent " + agentId + " seed=" + botSeed), true);
    return 1;
  }

  private static int agentSpawnAt(CommandContext<ServerCommandSource> ctx) {
    ServerWorld world = world(ctx);
    String agentId = StringArgumentType.getString(ctx, "agentId");
    int botSeed = IntegerArgumentType.getInteger(ctx, "botSeed");
    int x = IntegerArgumentType.getInteger(ctx, "startX");
    int y = IntegerArgumentType.getInteger(ctx, "startY");
    int z = IntegerArgumentType.getInteger(ctx, "startZ");

    ResearchExperimentConfig cfg = new ResearchExperimentConfig();
    cfg.protocolId = "PASSIVE_WANDER";
    cfg.durationTicks = 2000;
    cfg.allowedRadiusChunks = 8;
    cfg.botSeed = botSeed;
    cfg.experimentSeed = botSeed;
    cfg.experimentId = "exp_" + agentId;
    cfg.loggingLabel = "spawn";
    cfg.startingPos = new BlockPos(x, y, z);

    ResearchExperimentManager.spawnAgent(world, agentId, cfg);
    ctx.getSource().sendFeedback(() -> Text.literal(
        "Spawned agent " + agentId + " seed=" + botSeed + " at (" + x + "," + y + "," + z + ")"), true);
    return 1;
  }

  private static int agentStop(CommandContext<ServerCommandSource> ctx) {
    ServerWorld world = world(ctx);
    String agentId = StringArgumentType.getString(ctx, "agentId");
    ResearchExperimentManager.stopAgent(world, agentId);
    ctx.getSource().sendFeedback(() -> Text.literal("Stopped agent " + agentId), true);
    return 1;
  }

  private static int agentStatus(CommandContext<ServerCommandSource> ctx) {
    ServerWorld world = world(ctx);
    String agentId = StringArgumentType.getString(ctx, "agentId");
    ResearchPlayerSnapshot snapshot = ResearchExperimentManager.status(world, agentId);
    if (snapshot == null) {
      ctx.getSource().sendError(Text.literal("Agent not found: " + agentId));
      return 0;
    }
    ctx.getSource().sendFeedback(() -> Text.literal(
        "agent=" + snapshot.agentId + " protocol=" + snapshot.protocolId + " exp=" + snapshot.experimentId
            + " running=" + snapshot.running + " paused=" + snapshot.paused
            + " seed=" + snapshot.botSeed + " expSeed=" + snapshot.experimentSeed), true);
    return 1;
  }

  private static int expMineCoal(CommandContext<ServerCommandSource> ctx) {
    ServerWorld world = world(ctx);
    return assignMining(ctx, world, "COAL", "MINE_COAL");
  }

  private static int expMineIron(CommandContext<ServerCommandSource> ctx) {
    ServerWorld world = world(ctx);
    return assignMining(ctx, world, "IRON", "MINE_IRON");
  }

  private static int assignMining(CommandContext<ServerCommandSource> ctx, ServerWorld world, String oreId, String protocolId) {
    String agentId = StringArgumentType.getString(ctx, "agentId");
    int durationTicks = IntegerArgumentType.getInteger(ctx, "durationTicks");
    int allowedRadiusChunks = IntegerArgumentType.getInteger(ctx, "allowedRadiusChunks");
    int goalCount = IntegerArgumentType.getInteger(ctx, "goalCount");
    int experimentSeed = IntegerArgumentType.getInteger(ctx, "experimentSeed");
    String loggingLabel = StringArgumentType.getString(ctx, "loggingLabel");

    ResearchExperimentConfig cfg = new ResearchExperimentConfig();
    cfg.protocolId = protocolId;
    cfg.durationTicks = durationTicks;
    cfg.allowedRadiusChunks = allowedRadiusChunks;
    cfg.miningOreId = oreId;
    cfg.miningGoalCount = goalCount;
    cfg.experimentSeed = experimentSeed;
    cfg.loggingLabel = loggingLabel;
    cfg.experimentId = "exp_" + agentId + "_" + protocolId + "_" + loggingLabel;

    // If the agent doesn't exist, we spawn it with the experimentSeed as botSeed fallback.
    cfg.botSeed = experimentSeed;
    ResearchExperimentManager.assignExperiment(world, agentId, cfg);
    ctx.getSource().sendFeedback(() -> Text.literal("Assigned " + protocolId + " to " + agentId + " duration=" + durationTicks), true);
    return 1;
  }

  private static int expExplore(CommandContext<ServerCommandSource> ctx) {
    ServerWorld world = world(ctx);
    String agentId = StringArgumentType.getString(ctx, "agentId");
    int durationTicks = IntegerArgumentType.getInteger(ctx, "durationTicks");
    int allowedRadiusChunks = IntegerArgumentType.getInteger(ctx, "allowedRadiusChunks");
    int experimentSeed = IntegerArgumentType.getInteger(ctx, "experimentSeed");
    String loggingLabel = StringArgumentType.getString(ctx, "loggingLabel");

    ResearchExperimentConfig cfg = new ResearchExperimentConfig();
    cfg.protocolId = "EXPLORE";
    cfg.durationTicks = durationTicks;
    cfg.allowedRadiusChunks = allowedRadiusChunks;
    cfg.experimentSeed = experimentSeed;
    cfg.loggingLabel = loggingLabel;
    cfg.experimentId = "exp_" + agentId + "_EXPLORE_" + loggingLabel;
    cfg.botSeed = experimentSeed;

    ResearchExperimentManager.assignExperiment(world, agentId, cfg);
    ctx.getSource().sendFeedback(() -> Text.literal("Assigned EXPLORE to " + agentId + " duration=" + durationTicks), true);
    return 1;
  }

  private static int expWander(CommandContext<ServerCommandSource> ctx) {
    ServerWorld world = world(ctx);
    String agentId = StringArgumentType.getString(ctx, "agentId");
    int durationTicks = IntegerArgumentType.getInteger(ctx, "durationTicks");
    int allowedRadiusChunks = IntegerArgumentType.getInteger(ctx, "allowedRadiusChunks");
    int experimentSeed = IntegerArgumentType.getInteger(ctx, "experimentSeed");
    String loggingLabel = StringArgumentType.getString(ctx, "loggingLabel");

    ResearchExperimentConfig cfg = new ResearchExperimentConfig();
    cfg.protocolId = "PASSIVE_WANDER";
    cfg.durationTicks = durationTicks;
    cfg.allowedRadiusChunks = allowedRadiusChunks;
    cfg.experimentSeed = experimentSeed;
    cfg.loggingLabel = loggingLabel;
    cfg.experimentId = "exp_" + agentId + "_WANDER_" + loggingLabel;
    cfg.botSeed = experimentSeed;

    ResearchExperimentManager.assignExperiment(world, agentId, cfg);
    ctx.getSource().sendFeedback(() -> Text.literal("Assigned PASSIVE_WANDER to " + agentId + " duration=" + durationTicks), true);
    return 1;
  }

  private static int expBuildShelter(CommandContext<ServerCommandSource> ctx) {
    ServerWorld world = world(ctx);
    String agentId = StringArgumentType.getString(ctx, "agentId");
    int durationTicks = IntegerArgumentType.getInteger(ctx, "durationTicks");
    int allowedRadiusChunks = IntegerArgumentType.getInteger(ctx, "allowedRadiusChunks");
    int experimentSeed = IntegerArgumentType.getInteger(ctx, "experimentSeed");
    String loggingLabel = StringArgumentType.getString(ctx, "loggingLabel");

    ResearchExperimentConfig cfg = new ResearchExperimentConfig();
    cfg.protocolId = "BUILD_SHELTER";
    cfg.durationTicks = durationTicks;
    cfg.allowedRadiusChunks = allowedRadiusChunks;
    cfg.experimentSeed = experimentSeed;
    cfg.loggingLabel = loggingLabel;
    cfg.experimentId = "exp_" + agentId + "_BUILD_" + loggingLabel;
    cfg.botSeed = experimentSeed;

    ResearchExperimentManager.assignExperiment(world, agentId, cfg);
    ctx.getSource().sendFeedback(() -> Text.literal("Assigned BUILD_SHELTER to " + agentId + " duration=" + durationTicks), true);
    return 1;
  }

  private static int expCombat(CommandContext<ServerCommandSource> ctx) {
    ServerWorld world = world(ctx);
    String agentId = StringArgumentType.getString(ctx, "agentId");
    int durationTicks = IntegerArgumentType.getInteger(ctx, "durationTicks");
    int allowedRadiusChunks = IntegerArgumentType.getInteger(ctx, "allowedRadiusChunks");
    int experimentSeed = IntegerArgumentType.getInteger(ctx, "experimentSeed");
    String loggingLabel = StringArgumentType.getString(ctx, "loggingLabel");

    ResearchExperimentConfig cfg = new ResearchExperimentConfig();
    cfg.protocolId = "COMBAT";
    cfg.durationTicks = durationTicks;
    cfg.allowedRadiusChunks = allowedRadiusChunks;
    cfg.experimentSeed = experimentSeed;
    cfg.loggingLabel = loggingLabel;
    cfg.experimentId = "exp_" + agentId + "_COMBAT_" + loggingLabel;
    cfg.botSeed = experimentSeed;

    ResearchExperimentManager.assignExperiment(world, agentId, cfg);
    ctx.getSource().sendFeedback(() -> Text.literal("Assigned COMBAT to " + agentId + " duration=" + durationTicks), true);
    return 1;
  }

  private static int expAssignGeneric(CommandContext<ServerCommandSource> ctx) {
    ServerWorld world = world(ctx);
    String agentId = StringArgumentType.getString(ctx, "agentId");
    String protocolId = StringArgumentType.getString(ctx, "protocolId").toUpperCase();
    int durationTicks = IntegerArgumentType.getInteger(ctx, "durationTicks");
    int allowedRadiusChunks = IntegerArgumentType.getInteger(ctx, "allowedRadiusChunks");
    boolean fieldEnabled = BoolArgumentType.getBool(ctx, "fieldEnabled");
    String couplingPresetId = StringArgumentType.getString(ctx, "couplingPresetId");
    int experimentSeed = IntegerArgumentType.getInteger(ctx, "experimentSeed");
    String loggingLabel = StringArgumentType.getString(ctx, "loggingLabel");

    ResearchExperimentConfig cfg = new ResearchExperimentConfig();
    cfg.protocolId = protocolId;
    cfg.durationTicks = durationTicks;
    cfg.allowedRadiusChunks = allowedRadiusChunks;
    cfg.fieldEnabled = fieldEnabled;
    cfg.couplingPresetId = couplingPresetId;
    cfg.experimentSeed = experimentSeed;
    cfg.botSeed = experimentSeed;
    cfg.loggingLabel = loggingLabel;
    cfg.experimentId = "exp_" + agentId + "_" + protocolId + "_" + loggingLabel;

    // Provide minimal mining defaults for mining protocols.
    if ("MINE_IRON".equals(protocolId)) {
      cfg.miningOreId = "IRON";
      cfg.miningGoalCount = cfg.miningGoalCount <= 0 ? 8 : cfg.miningGoalCount;
    } else if ("MINE_COAL".equals(protocolId)) {
      cfg.miningOreId = "COAL";
      cfg.miningGoalCount = cfg.miningGoalCount <= 0 ? 16 : cfg.miningGoalCount;
    }

    ResearchExperimentManager.assignExperiment(world, agentId, cfg);
    ctx.getSource().sendFeedback(() -> Text.literal(
        "Assigned protocol=" + protocolId + " to " + agentId + " duration=" + durationTicks
            + " fieldEnabled=" + fieldEnabled + " coupling=" + couplingPresetId), true);
    return 1;
  }

  private static int expFarmer(CommandContext<ServerCommandSource> ctx) {
    ServerWorld world = world(ctx);
    String agentId = StringArgumentType.getString(ctx, "agentId");
    int durationTicks = IntegerArgumentType.getInteger(ctx, "durationTicks");
    int allowedRadiusChunks = IntegerArgumentType.getInteger(ctx, "allowedRadiusChunks");
    int experimentSeed = IntegerArgumentType.getInteger(ctx, "experimentSeed");
    String loggingLabel = StringArgumentType.getString(ctx, "loggingLabel");

    ResearchExperimentConfig cfg = new ResearchExperimentConfig();
    cfg.protocolId = "FARMER";
    cfg.durationTicks = durationTicks;
    cfg.allowedRadiusChunks = allowedRadiusChunks;
    cfg.experimentSeed = experimentSeed;
    cfg.loggingLabel = loggingLabel;
    cfg.experimentId = "exp_" + agentId + "_FARMER_" + loggingLabel;
    cfg.botSeed = experimentSeed;

    ResearchExperimentManager.assignExperiment(world, agentId, cfg);
    ctx.getSource().sendFeedback(() -> Text.literal("Assigned FARMER to " + agentId + " duration=" + durationTicks), true);
    return 1;
  }

  private static int expTrade(CommandContext<ServerCommandSource> ctx) {
    ServerWorld world = world(ctx);
    String agentId = StringArgumentType.getString(ctx, "agentId");
    int durationTicks = IntegerArgumentType.getInteger(ctx, "durationTicks");
    int allowedRadiusChunks = IntegerArgumentType.getInteger(ctx, "allowedRadiusChunks");
    int experimentSeed = IntegerArgumentType.getInteger(ctx, "experimentSeed");
    String loggingLabel = StringArgumentType.getString(ctx, "loggingLabel");

    ResearchExperimentConfig cfg = new ResearchExperimentConfig();
    cfg.protocolId = "TRADE";
    cfg.durationTicks = durationTicks;
    cfg.allowedRadiusChunks = allowedRadiusChunks;
    cfg.experimentSeed = experimentSeed;
    cfg.loggingLabel = loggingLabel;
    cfg.experimentId = "exp_" + agentId + "_TRADE_" + loggingLabel;
    cfg.botSeed = experimentSeed;

    ResearchExperimentManager.assignExperiment(world, agentId, cfg);
    ctx.getSource().sendFeedback(() -> Text.literal("Assigned TRADE to " + agentId + " duration=" + durationTicks), true);
    return 1;
  }

  private static int expTransport(CommandContext<ServerCommandSource> ctx) {
    ServerWorld world = world(ctx);
    String agentId = StringArgumentType.getString(ctx, "agentId");
    int durationTicks = IntegerArgumentType.getInteger(ctx, "durationTicks");
    int allowedRadiusChunks = IntegerArgumentType.getInteger(ctx, "allowedRadiusChunks");
    int experimentSeed = IntegerArgumentType.getInteger(ctx, "experimentSeed");
    String loggingLabel = StringArgumentType.getString(ctx, "loggingLabel");

    ResearchExperimentConfig cfg = new ResearchExperimentConfig();
    cfg.protocolId = "RESOURCE_TRANSPORT";
    cfg.durationTicks = durationTicks;
    cfg.allowedRadiusChunks = allowedRadiusChunks;
    cfg.experimentSeed = experimentSeed;
    cfg.loggingLabel = loggingLabel;
    cfg.experimentId = "exp_" + agentId + "_RESOURCE_TRANSPORT_" + loggingLabel;
    cfg.botSeed = experimentSeed;

    ResearchExperimentManager.assignExperiment(world, agentId, cfg);
    ctx.getSource().sendFeedback(() -> Text.literal("Assigned RESOURCE_TRANSPORT to " + agentId + " duration=" + durationTicks), true);
    return 1;
  }

  private static int remove(CommandContext<ServerCommandSource> ctx) {
    ResearchPlayerController.remove(world(ctx));
    ctx.getSource().sendFeedback(() -> Text.literal("Research player removed."), true);
    return 1;
  }

  private static int start(CommandContext<ServerCommandSource> ctx) {
    ResearchPlayerController.start(world(ctx));
    ctx.getSource().sendFeedback(() -> Text.literal("Research player started."), true);
    return 1;
  }

  private static int stop(CommandContext<ServerCommandSource> ctx) {
    ResearchPlayerController.stop(world(ctx));
    ctx.getSource().sendFeedback(() -> Text.literal("Research player stopped."), true);
    return 1;
  }

  private static int pause(CommandContext<ServerCommandSource> ctx) {
    ResearchPlayerController.pause(world(ctx));
    ctx.getSource().sendFeedback(() -> Text.literal("Research player paused."), true);
    return 1;
  }

  private static int resume(CommandContext<ServerCommandSource> ctx) {
    ResearchPlayerController.resume(world(ctx));
    ctx.getSource().sendFeedback(() -> Text.literal("Research player resumed."), true);
    return 1;
  }

  private static int reset(CommandContext<ServerCommandSource> ctx) {
    ResearchPlayerController.reset(world(ctx), ResearchConfig.getBotSeed());
    ctx.getSource().sendFeedback(() -> Text.literal("Research player reset."), true);
    return 1;
  }

  private static int status(CommandContext<ServerCommandSource> ctx) {
    ServerWorld world = world(ctx);
    ResearchPlayerSnapshot snapshot = ResearchPlayerController.status(world);
    ServerPlayerEntity player = ResearchPlayerRegistry.get(world);
    BlockPos pos = player != null ? player.getBlockPos() : BlockPos.ORIGIN;
    ctx.getSource().sendFeedback(() -> Text.literal(String.format(
        "state=%s prev=%s running=%s paused=%s seed=%d pos=(%d,%d,%d) reason=%s",
        snapshot.state, snapshot.previousState, snapshot.running, snapshot.paused,
        snapshot.botSeed, pos.getX(), pos.getY(), pos.getZ(), snapshot.transitionReason)), false);
    return 1;
  }

  private static int seed(CommandContext<ServerCommandSource> ctx) {
    int seed = IntegerArgumentType.getInteger(ctx, "seed");
    ResearchConfig.setBotSeed(seed);
    ResearchPlayerController.status(world(ctx)).botSeed = seed;
    ctx.getSource().sendFeedback(() -> Text.literal("Research bot seed set to " + seed), true);
    return 1;
  }

  private static int gotoState(CommandContext<ServerCommandSource> ctx) {
    String raw = StringArgumentType.getString(ctx, "state");
    ResearchPlayerState state = ResearchPlayerState.parse(raw);
    if (state == null) {
      ctx.getSource().sendError(Text.literal("Unknown state: " + raw));
      return 0;
    }
    ResearchPlayerController.gotoState(world(ctx), state);
    ctx.getSource().sendFeedback(() -> Text.literal("Research player state -> " + state.name()), true);
    return 1;
  }

  private static int home(CommandContext<ServerCommandSource> ctx) {
    ServerCommandSource source = ctx.getSource();
    ServerPlayerEntity player = ResearchPlayerRegistry.get(world(ctx));
    BlockPos pos = player != null ? player.getBlockPos() : source.getPlayer() != null
        ? source.getPlayer().getBlockPos() : world(ctx).getSpawnPos();
    ResearchPlayerController.setHome(world(ctx), pos);
    ctx.getSource().sendFeedback(() -> Text.literal("Research player home set to " + pos), true);
    return 1;
  }

  private static int logOn(CommandContext<ServerCommandSource> ctx) {
    ResearchPlayerController.status(world(ctx)).loggingEnabled = true;
    ResearchPlayerLogger.setEnabled(true);
    ctx.getSource().sendFeedback(() -> Text.literal("Research player logging enabled."), true);
    return 1;
  }

  private static int logOff(CommandContext<ServerCommandSource> ctx) {
    ResearchPlayerController.status(world(ctx)).loggingEnabled = false;
    ResearchPlayerLogger.setEnabled(false);
    ctx.getSource().sendFeedback(() -> Text.literal("Research player logging disabled."), true);
    return 1;
  }
}
