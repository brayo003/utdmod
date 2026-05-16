package com.utdmod.tick;

import com.utdmod.core.TensionEvent;
import com.utdmod.core.TensionManager;
import com.utdmod.core.TensionTraceLogger;
import com.utdmod.diag.DiagnosticLogs;
import com.utdmod.tension.ChunkTensionData;
import com.utdmod.tension.ChunkTensionManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

/**
 * Structured tension activity logging; emits legacy [TRACE] line plus [TENSION_TRACE] block.
 */
public final class TensionLogger {

    public static boolean ENABLED = true;

    public static void log(
        long tick,
        String source,
        String detail,
        double delta,
        double before,
        double after,
        ChunkPos chunk,
        ServerPlayerEntity player
    ) {
        log(tick, source, detail, delta, before, after, chunk, player, null);
    }

    public static void log(
        long tick,
        String source,
        String detail,
        double delta,
        double before,
        double after,
        ChunkPos chunk,
        ServerPlayerEntity player,
        ServerWorld worldForState
    ) {
        if (!ENABLED) return;
        String playerName = player != null ? player.getName().getString() : "SYSTEM";
        ChunkPos c = chunk != null ? chunk : new ChunkPos(0, 0);
        TensionTraceLogger.log(new TensionEvent(tick, playerName, source, detail, delta, before, after, c));

        ServerWorld sw = worldForState != null ? worldForState : (player != null ? player.getServerWorld() : null);
        ChunkTensionData.ChunkState chunkState = sw != null ? ChunkTensionManager.getChunkState(sw, c) : ChunkTensionData.ChunkState.STABLE;
        String state = DiagnosticLogs.chunkStateLogName(chunkState);
        double g = TensionManager.getTension();
        TensionTraceLogger.traceStructured(
            tick,
            playerName,
            source,
            source,
            detail != null ? detail : "",
            c,
            delta,
            0.0,
            after,
            g,
            state,
            "global_delta_couples_at_tick_end"
        );
        String approxBefore = DiagnosticLogs.approximateState(before);
        String approxAfter = DiagnosticLogs.approximateState(after);
        boolean crossed = !approxBefore.equals(approxAfter);
        DiagnosticLogs.eventEffect(
            source,
            detail != null ? detail : "",
            c,
            delta,
            before,
            after,
            crossed,
            state,
            tick
        );
    }

    /** Use when global tension changes in the same call stack (items, rituals, stabilizers). */
    public static void traceWithGlobals(
        long tick,
        String category,
        String action,
        String detail,
        ChunkPos chunk,
        ServerPlayerEntity player,
        ServerWorld world,
        double localDelta,
        double localAfter,
        double globalBefore,
        double globalAfter
    ) {
        if (!ENABLED) return;
        String playerName = player != null ? player.getName().getString() : "SYSTEM";
        ChunkPos c = chunk != null ? chunk : new ChunkPos(0, 0);
        double localBefore = localAfter - localDelta;
        TensionTraceLogger.log(new TensionEvent(tick, playerName, category, detail, localDelta, localBefore, localAfter, c));
        ChunkTensionData.ChunkState chunkState = world != null ? ChunkTensionManager.getChunkState(world, c) : ChunkTensionData.ChunkState.STABLE;
        String state = DiagnosticLogs.chunkStateLogName(chunkState);
        TensionTraceLogger.traceStructured(
            tick,
            playerName,
            category,
            action,
            detail != null ? detail : "",
            c,
            localDelta,
            globalAfter - globalBefore,
            localAfter,
            globalAfter,
            state,
            ""
        );
        String approxBefore = DiagnosticLogs.approximateState(localBefore);
        String approxAfter = DiagnosticLogs.approximateState(localAfter);
        boolean crossed = !approxBefore.equals(approxAfter);
        DiagnosticLogs.eventEffect(
            category,
            detail != null ? detail : "",
            c,
            localDelta,
            localBefore,
            localAfter,
            crossed,
            state,
            tick
        );
    }
}
