package com.utdmod.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.utdmod.research.PerturbationRecorder;
import com.utdmod.research.PerturbationType;
import com.utdmod.research.ReplayAnalysisSupport;
import com.utdmod.research.ResearchConfig;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class ResearchCommands {

    private ResearchCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) ->
            dispatcher.register(CommandManager.literal("utdres")
                .requires(s -> s.hasPermissionLevel(2))
                .then(CommandManager.literal("enable")
                    .then(CommandManager.argument("type", StringArgumentType.word())
                        .executes(ResearchCommands::enableType)))
                .then(CommandManager.literal("disable")
                    .then(CommandManager.argument("type", StringArgumentType.word())
                        .executes(ResearchCommands::disableType)))
                .then(CommandManager.literal("bot")
                    .then(CommandManager.literal("on").executes(ResearchCommands::botEnable))
                    .then(CommandManager.literal("off").executes(ResearchCommands::botDisable))
                    .then(CommandManager.literal("type")
                        .then(CommandManager.argument("type", StringArgumentType.word())
                            .executes(ResearchCommands::botType)))
                    .then(CommandManager.literal("seed")
                        .then(CommandManager.argument("seed", IntegerArgumentType.integer())
                            .executes(ResearchCommands::botSeed))))
                .then(CommandManager.literal("visual")
                    .then(CommandManager.argument("mode", StringArgumentType.word())
                        .executes(ResearchCommands::visualMode)))
                .then(CommandManager.literal("recording")
                    .then(CommandManager.argument("state", StringArgumentType.word())
                        .executes(ResearchCommands::recordingState)))
                .then(CommandManager.literal("status").executes(ResearchCommands::status))
                .then(CommandManager.literal("snapshot").executes(ResearchCommands::snapshot))
                .then(CommandManager.literal("dump").executes(ResearchCommands::dump))));
    }

    private static int enableType(CommandContext<ServerCommandSource> ctx) {
        String raw = StringArgumentType.getString(ctx, "type");
        PerturbationType type = ResearchConfig.parseType(raw);
        if (type == null) {
            ctx.getSource().sendError(Text.literal("Unknown perturbation type: " + raw));
            return 0;
        }
        ResearchConfig.setEnabled(type, true);
        ctx.getSource().sendFeedback(() -> Text.literal("Enabled research perturbation type: " + type.name()), false);
        return 1;
    }

    private static int disableType(CommandContext<ServerCommandSource> ctx) {
        String raw = StringArgumentType.getString(ctx, "type");
        PerturbationType type = ResearchConfig.parseType(raw);
        if (type == null) {
            ctx.getSource().sendError(Text.literal("Unknown perturbation type: " + raw));
            return 0;
        }
        ResearchConfig.setEnabled(type, false);
        ctx.getSource().sendFeedback(() -> Text.literal("Disabled research perturbation type: " + type.name()), false);
        return 1;
    }

    private static int botEnable(CommandContext<ServerCommandSource> ctx) {
        ResearchConfig.setBotEnabled(true);
        ctx.getSource().sendFeedback(() -> Text.literal("Research bot enabled."), false);
        return 1;
    }

    private static int botDisable(CommandContext<ServerCommandSource> ctx) {
        ResearchConfig.setBotEnabled(false);
        ctx.getSource().sendFeedback(() -> Text.literal("Research bot disabled."), false);
        return 1;
    }

    private static int botType(CommandContext<ServerCommandSource> ctx) {
        String raw = StringArgumentType.getString(ctx, "type");
        ResearchConfig.setBotType(raw);
        ctx.getSource().sendFeedback(() -> Text.literal("Research bot type set to " + ResearchConfig.getBotType()), false);
        return 1;
    }

    private static int botSeed(CommandContext<ServerCommandSource> ctx) {
        int seed = IntegerArgumentType.getInteger(ctx, "seed");
        ResearchConfig.setBotSeed(seed);
        ctx.getSource().sendFeedback(() -> Text.literal("Research bot seed set to " + ResearchConfig.getBotSeed()), false);
        return 1;
    }

    private static int visualMode(CommandContext<ServerCommandSource> ctx) {
        String raw = StringArgumentType.getString(ctx, "mode");
        ResearchConfig.setVisualMode(raw);
        ctx.getSource().sendFeedback(() -> Text.literal("Research visual mode set to " + ResearchConfig.getVisualMode()), false);
        return 1;
    }

    private static int recordingState(CommandContext<ServerCommandSource> ctx) {
        String raw = StringArgumentType.getString(ctx, "state");
        boolean enabled = "on".equalsIgnoreCase(raw) || "true".equalsIgnoreCase(raw) || "enable".equalsIgnoreCase(raw);
        ResearchConfig.setRecordingEnabled(enabled);
        ctx.getSource().sendFeedback(() -> Text.literal("Research recording " + (enabled ? "enabled" : "disabled")), false);
        return 1;
    }

    private static int status(CommandContext<ServerCommandSource> ctx) {
        ctx.getSource().sendFeedback(() -> Text.literal("Research event filters: " + ResearchConfig.getEnabledTypesSummary()), false);
        ctx.getSource().sendFeedback(() -> Text.literal("Bot enabled=" + ResearchConfig.isBotEnabled() + " type=" + ResearchConfig.getBotType() + " seed=" + ResearchConfig.getBotSeed()), false);
        ctx.getSource().sendFeedback(() -> Text.literal("Recording enabled=" + ResearchConfig.isRecordingEnabled() + " visualMode=" + ResearchConfig.getVisualMode()), false);
        ctx.getSource().sendFeedback(() -> Text.literal("Recorded perturbations: " + PerturbationRecorder.count()), false);
        return 1;
    }

    private static int dump(CommandContext<ServerCommandSource> ctx) {
        PerturbationRecorder.dumpRecent(50);
        ctx.getSource().sendFeedback(() -> Text.literal("Dumped recent perturbations to server log."), false);
        return 1;
    }

    private static int snapshot(CommandContext<ServerCommandSource> ctx) {
        var source = ctx.getSource();
        var world = source.getWorld();
        var snapshot = ReplayAnalysisSupport.captureSubstrateSnapshot(world);
        ReplayAnalysisSupport.logComparison(source.getServer(), "utdres_snapshot");
        source.sendFeedback(() -> Text.literal(
            "Captured substrate snapshot: " + snapshot.size() + " chunks in "
                + world.getRegistryKey().getValue()), false);
        return 1;
    }
}
