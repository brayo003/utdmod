package com.utdmod.experiment;

import com.utdmod.diag.DiagnosticLogs;
import com.utdmod.tension.ChunkTensionData;
import com.utdmod.tension.ChunkTensionManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Sparse experiment markers for manual validation (no gameplay effect).
 */
public final class ExperimentTelemetry {

    private static ChunkPos test2Origin;
    /** World-time anchor for TEST1 deltas (set via {@link com.utdmod.command.ExperimentCommands}). */
    private static long anchorWorldTick = Long.MIN_VALUE;

    private static final Map<ChunkPos, Test1> TEST1 = new HashMap<>();

    private ExperimentTelemetry() {}

    public static void setAnchorWorldTick(long tick) {
        anchorWorldTick = tick;
    }

    public static void setTest2Origin(ChunkPos origin) {
        test2Origin = new ChunkPos(origin.x, origin.z);
        System.out.printf("[TEST2] origin_set chunk=%d,%d%n", origin.x, origin.z);
    }

    public static void clearTest2Origin() {
        test2Origin = null;
    }

    public static void resetAll() {
        test2Origin = null;
        anchorWorldTick = Long.MIN_VALUE;
        TEST1.clear();
    }

    public static void onChunkStateTransition(
        ChunkPos pos,
        ChunkTensionData.ChunkState oldState,
        ChunkTensionData.ChunkState newState,
        double tension,
        long worldTick
    ) {
        if (newState == ChunkTensionData.ChunkState.STRAINED && oldState == ChunkTensionData.ChunkState.STABLE) {
            Test1 t = TEST1.computeIfAbsent(new ChunkPos(pos.x, pos.z), k -> new Test1());
            if (t.strainedWorldTick < 0) {
                t.strainedWorldTick = worldTick;
            }
        }
        if (newState == ChunkTensionData.ChunkState.FRACTURED
            && (oldState == ChunkTensionData.ChunkState.STRAINED || oldState == ChunkTensionData.ChunkState.STABLE)) {
            Test1 t = TEST1.computeIfAbsent(new ChunkPos(pos.x, pos.z), k -> new Test1());
            if (t.fracturedWorldTick < 0) {
                t.fracturedWorldTick = worldTick;
            }
            long tts = anchorWorldTick != Long.MIN_VALUE && t.strainedWorldTick >= 0
                ? t.strainedWorldTick - anchorWorldTick
                : -1L;
            long ttf = t.strainedWorldTick >= 0 ? worldTick - t.strainedWorldTick : -1L;
            DiagnosticLogs.test1("FRACTURE", pos, tts, ttf, tension);
        }
    }

    public static void tick(MinecraftServer server, long tick) {
        if (test2Origin != null && tick % 200 == 0) {
            runTest2(server);
        }
    }

    private static void runTest2(MinecraftServer server) {
        int ox = test2Origin.x;
        int oz = test2Origin.z;
        double sum = 0.0;
        int n = 0;
        int maxR = 0;
        Set<ChunkPos> affected = new HashSet<>();
        for (ServerWorld sw : server.getWorlds()) {
            ChunkTensionData data = ChunkTensionData.getServerState(sw);
            for (ChunkPos cp : data.getTensionMap().keySet()) {
                if (!sw.isChunkLoaded(cp.x, cp.z)) continue;
                double t = ChunkTensionManager.getLocalTension(sw, cp);
                if (t < 0.22) continue;
                int dx = Math.abs(cp.x - ox);
                int dz = Math.abs(cp.z - oz);
                int r = Math.max(dx, dz);
                if (r <= 6) {
                    affected.add(cp);
                    sum += t;
                    n++;
                    maxR = Math.max(maxR, r);
                }
            }
        }
        double avg = n > 0 ? sum / n : 0.0;
        DiagnosticLogs.test2(test2Origin, affected.size(), maxR, avg);
    }

    private static final class Test1 {
        long strainedWorldTick = -1;
        long fracturedWorldTick = -1;
    }
}
