package com.utdmod.core;

import com.utdmod.diag.DiagnosticLogs;
import com.utdmod.tension.ChunkTensionManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

/**
 * Global tension field. Dynamics use unified parameters; inflow is dominated by chunk aggregation
 * (see {@link TensionServerTick}) plus optional impulses (debug, rare global events).
 */
public final class TensionManager {
    private static double globalTension = 0.0;
    private static boolean stormActive = false;
    private static final double T_MAX = 5.0;
    /** Slightly faster decay so global returns to calm when coupling drops. */
    private static final double LAMBDA = 0.0032;
    /** Quadratic reinjection: keep small to avoid locking g≈T_MAX when coupling spikes. */
    private static final double ALPHA = 0.002;
    private static final double STORM_THRESHOLD = 1.0;
    private static final double STORM_HYSTERESIS = 0.7;
    private static double impulseBuffer = 0.0;

    private static final double MIN_LAMBDA = 1e-6;
    private static final double MAX_ALPHA = 1.0;

    private static final double[] CORRUPTION_THRESHOLDS = {0.8, 1.5, 2.5, 4.0};
    private static final String[] CORRUPTION_LEVELS = {"Subtle", "Moderate", "Severe", "Catastrophic"};
    private static int currentCorruptionLevel = 0;

    private TensionManager() {}

    public static double getTension() {
        return globalTension;
    }

    public static boolean isStormActive() {
        return stormActive;
    }

    public static void setTension(double tension) {
        globalTension = Math.max(0.0, Math.min(T_MAX, tension));
        updateCorruptionState();
    }

    /**
     * Buffered impulse used for debug spikes, rare scripted events, or legacy call sites.
     * Player walking, mining, and combat should affect chunks via {@link ChunkTensionManager}, not this.
     */
    public static void addEvent(double amount) {
        impulseBuffer += amount;
    }

    /**
     * @deprecated Prefer {@link ChunkTensionManager#addMiningTension}.
     */
    @Deprecated
    public static void addMiningTension(String blockId) {
        // Intentionally not applied globally; retained for binary compatibility if referenced.
    }

    public static void addMiningTension(String blockId, ServerPlayerEntity player) {
        if (player == null || player.getServer() == null) return;
        ChunkTensionManager.addMiningTension(player.getServerWorld(), new ChunkPos(player.getBlockPos()), blockId);
    }

    public static void addDeforestationTension() {
        // Local-only hook; implement via ChunkTensionManager when wired.
    }

    /**
     * Advances global tension for one server tick.
     *
     * @param chunkCouplingInflow  drive from chunk statistics (unchanged total applied to dynamics)
     * @param stormDiagnosticsNote storm-related diagnostic scalar for logs
     * @param ambientInflowDiag    mean-term coupling (logging only; with localCouplingDiag should match chunk coupling)
     * @param localCouplingDiag    hotspot coupling terms (logging only)
     */
    public static void tickGlobalDynamics(
        double chunkCouplingInflow,
        double stormDiagnosticsNote,
        long serverTick,
        double ambientInflowDiag,
        double localCouplingDiag
    ) {
        int corruptionAtStart = currentCorruptionLevel;
        boolean stormAtStart = stormActive;

        double g0 = globalTension;
        double ambientDecay = -LAMBDA * g0;
        double nonlinear = ALPHA * g0 * g0;
        double ritualSnap = impulseBuffer;
        double inflow = chunkCouplingInflow + ritualSnap;

        globalTension += ambientDecay + nonlinear + inflow;

        globalTension = Math.max(0.0, Math.min(T_MAX, globalTension));

        impulseBuffer *= 0.88;

        if (globalTension > STORM_THRESHOLD && !stormActive) {
            stormActive = true;
        }
        if (globalTension < STORM_HYSTERESIS) {
            stormActive = false;
        }

        updateCorruptionState();
        checkEquilibriumStability(inflow);

        if (currentCorruptionLevel != corruptionAtStart) {
            TensionTraceLogger.traceSystem(
                TensionTraceClock.getServerTick(),
                "CORRUPTION_TIER_CHANGE",
                corruptionAtStart + "->" + currentCorruptionLevel + ":" + getCorruptionLevelName(),
                globalTension,
                "inflow=" + String.format("%.5f", inflow)
            );
        }
        if (stormActive != stormAtStart) {
            TensionTraceLogger.traceSystem(
                TensionTraceClock.getServerTick(),
                stormActive ? "GLOBAL_STORM_START" : "GLOBAL_STORM_END",
                "hysteresis_threshold",
                globalTension,
                "stormActive=" + stormActive
            );
        }

        if (serverTick % 20 == 0) {
            double g1 = globalTension;
            double net = g1 - g0;
            DiagnosticLogs.globalFlowLine(
                serverTick,
                g1,
                localCouplingDiag,
                ambientInflowDiag,
                ambientDecay,
                nonlinear,
                ritualSnap,
                stormDiagnosticsNote,
                TensionActivityLedger.getMovement(),
                TensionActivityLedger.getMining(),
                TensionActivityLedger.getCombat(),
                net
            );
        }
    }

    private static void checkEquilibriumStability(double inflow) {
        double lambda = Math.max(LAMBDA, MIN_LAMBDA);
        double alpha = Math.min(ALPHA, MAX_ALPHA);
        double i = Math.max(0.0, inflow);
        double discriminant = lambda * lambda - 4.0 * alpha * i;
        if (discriminant < 0) {
            globalTension = Math.max(0.0, Math.min(T_MAX, globalTension * 0.9));
            TensionTraceLogger.traceSystem(
                TensionTraceClock.getServerTick(),
                "EQUILIBRIUM_DAMP",
                "discriminant<0 applied",
                globalTension,
                "inflow=" + String.format("%.5f", i)
            );
        }
    }

    public static int getCorruptionLevel() {
        return currentCorruptionLevel;
    }

    public static String getCorruptionLevelName() {
        return currentCorruptionLevel > 0
            ? CORRUPTION_LEVELS[Math.min(currentCorruptionLevel - 1, CORRUPTION_LEVELS.length - 1)]
            : "None";
    }

    private static void updateCorruptionState() {
        int newLevel = 0;
        for (int i = CORRUPTION_THRESHOLDS.length - 1; i >= 0; i--) {
            if (globalTension >= CORRUPTION_THRESHOLDS[i]) {
                newLevel = i + 1;
                break;
            }
        }
        if (newLevel != currentCorruptionLevel) {
            currentCorruptionLevel = newLevel;
        }
    }

    public static void reduceTension(double amount) {
        globalTension = Math.max(0.0, globalTension - amount);
        updateCorruptionState();
    }

    public static void triggerWraithEvent(World world) {
        // Spawn integration point
    }

    public static void triggerSerpentEvent(World world) {
        // Spawn integration point
    }

    public static TensionManager getServerState(MinecraftServer server) {
        return new TensionManager();
    }

    public float calculateBaseTension(ServerPlayerEntity player) {
        float tBase = 0.005f;
        if (player.getServer() != null) {
            tBase *= 1.0f;
        }
        return tBase;
    }
}
