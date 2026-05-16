package com.utdmod.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.utdmod.experiment.ExperimentTelemetry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;

public final class ExperimentCommands {

    private ExperimentCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) ->
            dispatcher.register(CommandManager.literal("utdexp")
                .requires(s -> s.hasPermissionLevel(2))
                .then(CommandManager.literal("anchor").executes(ExperimentCommands::anchor))
                .then(CommandManager.literal("origin")
                    .executes(ExperimentCommands::originPlayer)
                    .then(CommandManager.argument("chunkX", IntegerArgumentType.integer())
                        .then(CommandManager.argument("chunkZ", IntegerArgumentType.integer())
                            .executes(ExperimentCommands::originArgs))))
                .then(CommandManager.literal("origin_here").executes(ExperimentCommands::originHere))
                .then(CommandManager.literal("reset").executes(ExperimentCommands::reset))));
    }

    private static int anchor(CommandContext<ServerCommandSource> ctx) {
        long t = ctx.getSource().getWorld().getTime();
        ExperimentTelemetry.setAnchorWorldTick(t);
        ctx.getSource().sendFeedback(() -> Text.literal("[utdexp] anchor worldTick=" + t), false);
        return 1;
    }

    private static int originPlayer(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;
        ChunkPos cp = new ChunkPos(p.getBlockPos());
        ExperimentTelemetry.setTest2Origin(cp);
        ctx.getSource().sendFeedback(() -> Text.literal("[utdexp] TEST2 origin " + cp.x + "," + cp.z), false);
        return 1;
    }

    private static int originArgs(CommandContext<ServerCommandSource> ctx) {
        int x = IntegerArgumentType.getInteger(ctx, "chunkX");
        int z = IntegerArgumentType.getInteger(ctx, "chunkZ");
        ExperimentTelemetry.setTest2Origin(new ChunkPos(x, z));
        ctx.getSource().sendFeedback(() -> Text.literal("[utdexp] TEST2 origin " + x + "," + z), false);
        return 1;
    }

    private static int originHere(CommandContext<ServerCommandSource> ctx) {
        return originPlayer(ctx);
    }

    private static int reset(CommandContext<ServerCommandSource> ctx) {
        ExperimentTelemetry.resetAll();
        ctx.getSource().sendFeedback(() -> Text.literal("[utdexp] reset experiments"), false);
        return 1;
    }
}
