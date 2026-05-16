package com.utdmod.core;

import net.minecraft.util.math.ChunkPos;

/**
 * Human-readable terminal traces for gameplay tuning.
 */
public final class TensionTraceLogger {

    private TensionTraceLogger() {}

    /** Legacy single-line format (still emitted for grep compatibility). */
    public static void log(TensionEvent e) {
        System.out.printf(
            "[TRACE] tick=%d player=%s source=%s detail=%s chunk=%d,%d delta=%.5f before=%.5f after=%.5f%n",
            e.tick,
            e.player,
            e.source,
            e.detail,
            e.chunk.x,
            e.chunk.z,
            e.delta,
            e.before,
            e.after
        );
    }

    /**
     * Primary structured block for balancing (local + global snapshot on same tick as the action).
     */
    public static void traceStructured(
        long tick,
        String player,
        String category,
        String action,
        String detail,
        ChunkPos chunk,
        double localDelta,
        double globalDelta,
        double localTotal,
        double globalTotal,
        String chunkState,
        String propagation
    ) {
        int cx = chunk != null ? chunk.x : 0;
        int cz = chunk != null ? chunk.z : 0;
        System.out.println("[TENSION_TRACE]");
        System.out.printf("tick=%d%n", tick);
        System.out.printf("player=%s%n", player != null ? player : "SYSTEM");
        System.out.printf("category=%s%n", category);
        System.out.printf("action=%s%n", action);
        System.out.printf("detail=%s%n", detail != null ? detail : "");
        System.out.printf("chunk=%d,%d%n", cx, cz);
        System.out.printf("local_delta=%.5f%n", localDelta);
        System.out.printf("global_delta=%.5f%n", globalDelta);
        System.out.printf("local_total=%.5f%n", localTotal);
        System.out.printf("global_total=%.5f%n", globalTotal);
        System.out.printf("state=%s%n", chunkState != null ? chunkState : "UNKNOWN");
        if (propagation != null && !propagation.isEmpty()) {
            System.out.printf("propagation=%s%n", propagation);
        }
        System.out.println();
    }

    public static void traceField(
        long worldTime,
        String player,
        String action,
        String detail,
        ChunkPos chunk,
        double localTotal,
        double globalTotal,
        String chunkState,
        String propagation
    ) {
        traceStructured(
            worldTime,
            player,
            "FIELD",
            action,
            detail,
            chunk,
            0.0,
            0.0,
            localTotal,
            globalTotal,
            chunkState,
            propagation
        );
    }

    public static void traceSystem(long tick, String action, String detail, double globalTotal, String propagation) {
        traceStructured(
            tick,
            "SYSTEM",
            "SYSTEM",
            action,
            detail,
            new ChunkPos(0, 0),
            0.0,
            0.0,
            0.0,
            globalTotal,
            "N/A",
            propagation
        );
    }
}
