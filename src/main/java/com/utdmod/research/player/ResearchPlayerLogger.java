package com.utdmod.research.player;

import com.utdmod.UTDMod;
import com.utdmod.interpretation.ChunkInterpretation;
import com.utdmod.interpretation.InterpretationService;
import com.utdmod.interpretation.SubstrateSemantics;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

public final class ResearchPlayerLogger {
  private static final String LOG_ROOT = "logs/utd_research_player";
  private static BufferedWriter writer;
  private static String runId;
  private static boolean enabled = true;

  private ResearchPlayerLogger() {}

  public static void setEnabled(boolean value) {
    enabled = value;
  }

  public static synchronized void initialize(int botSeed) {
    if (writer != null) {
      return;
    }
    runId = Instant.now().toString().replace(':', '-') + "_seed" + botSeed;
    try {
      Path dir = Path.of(LOG_ROOT, runId);
      Files.createDirectories(dir);
      writer = Files.newBufferedWriter(
          dir.resolve("decisions.jsonl"),
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);
    } catch (IOException e) {
      UTDMod.LOGGER.error("[UTD] Failed to open research player log: {}", e.toString());
    }
  }

  public static synchronized void close() {
    if (writer != null) {
      try {
        writer.close();
      } catch (IOException ignored) {
      }
      writer = null;
    }
  }

  public static void log(ResearchPlayerContext ctx, long tick, String reason) {
    if (!enabled || writer == null || ctx == null) {
      return;
    }
    ServerPlayerEntity player = ctx.player;
    BlockPos pos = player.getBlockPos();

    BlockPos navTarget = ctx.currentTarget;
    double pathCost = navTarget == null ? -1.0 : ResearchPlayerEntityFactory.horizontalDistance(player, navTarget);

    // Local interpretation snapshot for the agent's current chunk.
    ChunkPos chunkPos = new ChunkPos(pos);
    ChunkInterpretation interp = InterpretationService.get(ctx.world, chunkPos);
    SubstrateSemantics sem = interp != null ? interp.semantics : null;
    double localFieldGradient = sem != null ? sem.localGradient : 0.0;
    double transportBiasX = sem != null ? sem.transportBiasX : 0.0;
    double transportBiasZ = sem != null ? sem.transportBiasZ : 0.0;

    // Global interpretation: simple world-average of semantics (best-effort).
    double gEcologicalStress = 0.0;
    double gMigrationPressure = 0.0;
    double gCorridorStrength = 0.0;
    int gCount = 0;
    try {
      for (ChunkInterpretation ci : InterpretationService.latestForWorld(ctx.world).values()) {
        if (ci == null || ci.semantics == null) continue;
        gEcologicalStress += ci.semantics.ecologicalStress;
        gMigrationPressure += ci.semantics.migrationPressure;
        gCorridorStrength += ci.semantics.corridorStrength;
        gCount++;
      }
    } catch (Exception ignored) {
      // Interpretations may not be ready during early ticks.
    }
    double globalEcologicalStress = gCount == 0 ? 0.0 : gEcologicalStress / gCount;
    double globalMigrationPressure = gCount == 0 ? 0.0 : gMigrationPressure / gCount;
    double globalCorridorStrength = gCount == 0 ? 0.0 : gCorridorStrength / gCount;

    String chosenAction = reason;
    String protocolId = ctx.snapshot.protocolId;
    String experimentId = ctx.snapshot.experimentId;
    String goal = inferGoal(ctx);
    String currentTask = protocolId;

    String line = buildJsonLine(tick, ctx, pos, player, navTarget, pathCost, reason,
        chosenAction, goal, currentTask, localFieldGradient, transportBiasX, transportBiasZ,
        globalEcologicalStress, globalMigrationPressure, globalCorridorStrength);
    try {
      writer.write(line);
    } catch (IOException e) {
      UTDMod.LOGGER.warn("[UTD] Research player log write failed: {}", e.toString());
    }
  }

  private static String buildJsonLine(long tick, ResearchPlayerContext ctx, BlockPos pos,
                                       ServerPlayerEntity player, BlockPos navTarget, double pathCost,
                                       String reason, String chosenAction, String goal, String currentTask,
                                       double localFieldGradient, double transportBiasX, double transportBiasZ,
                                       double globalEcologicalStress, double globalMigrationPressure,
                                       double globalCorridorStrength) {
    StringBuilder sb = new StringBuilder(512);
    sb.append('{');
    sb.append("\"tick\":").append(tick).append(',');
    sb.append("\"agentId\":\"").append(escape(ctx.snapshot.agentId)).append("\",");
    sb.append("\"experimentId\":\"").append(escape(ctx.snapshot.experimentId)).append("\",");
    sb.append("\"protocolId\":\"").append(escape(ctx.snapshot.protocolId)).append("\",");
    sb.append("\"loggingLabel\":\"").append(escape(ctx.snapshot.loggingLabel)).append("\",");
    sb.append("\"chosenAction\":\"").append(escape(chosenAction)).append("\",");
    sb.append("\"goal\":\"").append(escape(goal)).append("\",");
    sb.append("\"currentTask\":\"").append(escape(currentTask)).append("\",");
    sb.append("\"reason\":\"").append(escape(reason)).append("\",");

    sb.append("\"pos\":{\"x\":").append(pos.getX()).append(",\"y\":").append(pos.getY()).append(",\"z\":")
        .append(pos.getZ()).append("},");
    sb.append("\"yaw\":").append(String.format(java.util.Locale.US, "%.2f", player.getYaw())).append(',');
    sb.append("\"pitch\":").append(String.format(java.util.Locale.US, "%.2f", player.getPitch())).append(',');

    if (navTarget == null) {
      sb.append("\"navigationTarget\":null,");
    } else {
      sb.append("\"navigationTarget\":{\"x\":").append(navTarget.getX()).append(",\"y\":").append(navTarget.getY())
          .append(",\"z\":").append(navTarget.getZ()).append("},");
    }
    sb.append("\"pathCost\":").append(String.format(java.util.Locale.US, "%.4f", pathCost)).append(',');

    // Candidate actions: best-effort (protocol may only expose current target).
    if (navTarget == null) {
      sb.append("\"candidateActions\":[],");
    } else {
      sb.append("\"candidateActions\":[{\"type\":\"NAV_TO\",\"x\":").append(navTarget.getX()).append(",\"y\":")
          .append(navTarget.getY()).append(",\"z\":").append(navTarget.getZ()).append("}],");
    }

    sb.append("\"health\":").append(String.format(java.util.Locale.US, "%.1f", player.getHealth())).append(',');
    sb.append("\"hunger\":").append(player.getHungerManager().getFoodLevel()).append(',');
    sb.append("\"tool\":\"").append(escape(ctx.inventory.equippedToolName())).append("\",");
    sb.append("\"inventory\":\"").append(escape(ctx.inventory.summary())).append("\",");

    sb.append("\"fieldGradient\":").append(String.format(java.util.Locale.US, "%.6f", localFieldGradient)).append(',');
    sb.append("\"transportVector\":{\"x\":").append(String.format(java.util.Locale.US, "%.6f", transportBiasX))
        .append(",\"z\":").append(String.format(java.util.Locale.US, "%.6f", transportBiasZ)).append("},");

    sb.append("\"globalInterpretation\":{\"ecologicalStress\":")
        .append(String.format(java.util.Locale.US, "%.6f", globalEcologicalStress))
        .append(",\"migrationPressure\":")
        .append(String.format(java.util.Locale.US, "%.6f", globalMigrationPressure))
        .append(",\"corridorStrength\":")
        .append(String.format(java.util.Locale.US, "%.6f", globalCorridorStrength))
        .append("}");

    // Local substrate + local interpretation are expensive; we include semantics fields only where available.
    if (ctx.snapshot != null) {
      ChunkInterpretation ci = InterpretationService.get(ctx.world, new ChunkPos(pos));
      if (ci != null && ci.substrate != null && ci.semantics != null) {
        var st = ci.substrate;
        var s = ci.semantics;
        sb.append(",\"localSubstrate\":{\"activation\":").append(String.format(java.util.Locale.US, "%.6f", st.getActivation()))
            .append(",\"memory\":").append(String.format(java.util.Locale.US, "%.6f", st.getMemory()))
            .append(",\"transport\":").append(String.format(java.util.Locale.US, "%.6f", st.getTransport()))
            .append(",\"fieldDepth\":").append(String.format(java.util.Locale.US, "%.6f", st.getFieldDepth()))
            .append(",\"baseline\":").append(String.format(java.util.Locale.US, "%.6f", st.getBaseline()))
            .append("}");
        sb.append(",\"localInterpretation\":{\"ecologicalStress\":").append(String.format(java.util.Locale.US, "%.6f", s.ecologicalStress))
            .append(",\"migrationPressure\":").append(String.format(java.util.Locale.US, "%.6f", s.migrationPressure))
            .append(",\"corridorStrength\":").append(String.format(java.util.Locale.US, "%.6f", s.corridorStrength))
            .append(",\"territorialPressure\":").append(String.format(java.util.Locale.US, "%.6f", s.territorialPressure))
            .append(",\"roadFormationTendency\":").append(String.format(java.util.Locale.US, "%.6f", s.roadFormationTendency))
            .append("}");
      }
    }

    sb.append("}\n");
    return sb.toString();
  }

  private static String inferGoal(ResearchPlayerContext ctx) {
    if (ctx == null || ctx.snapshot == null) return "";
    String protocol = ctx.snapshot.protocolId;
    return switch (protocol) {
      case "MINE_COAL" -> "mine COAL >= " + ctx.snapshot.miningGoalCount;
      case "MINE_IRON" -> "mine IRON >= " + ctx.snapshot.miningGoalCount;
      case "BUILD_SHELTER" -> "build shelter";
      case "EXPLORE" -> "explore for " + ctx.snapshot.durationTicks + " ticks";
      case "COMBAT" -> "combat for " + ctx.snapshot.durationTicks + " ticks";
      case "PASSIVE_WANDER" -> "wander for " + ctx.snapshot.durationTicks + " ticks";
      default -> "protocol=" + protocol;
    };
  }

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
