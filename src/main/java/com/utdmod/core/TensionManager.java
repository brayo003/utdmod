package com.utdmod.core;

import com.utdmod.tension.ChunkTensionData;
import com.utdmod.tension.SubstrateObservationModel;
import com.utdmod.tension.SubstrateState;
import net.minecraft.server.MinecraftServer;
import java.util.Map;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

/**
 * Global tension summary — a read-only aggregate of substrate metrics.
 * There is no ODE here. Global tension is not an independently evolving state.
 * It is recomputed each tick from the substrate field.
 *
 * T_global = weighted mean of T(x) over all active chunks.
 */
public final class TensionManager {

    // Snapshot updated each tick by recompute()
    private static double globalMeanTension = 0.0;
    private static double globalMaxTension = 0.0;
    private static double globalVariance = 0.0;
    private static double globalActiveFraction = 0.0;
    private static double globalMeanDepth = 0.0;
    private static double globalMaxDepth = 0.0;
    private static double globalMeanMemory = 0.0;
    private static double globalMeanActivation = 0.0;
    private static boolean stormActive = false;

    private static final double STORM_THRESHOLD = 0.55;
    private static final double STORM_HYSTERESIS = 0.40;

    private static final double[] CORRUPTION_THRESHOLDS = {0.20, 0.40, 0.60, 0.80};
    private static final String[] CORRUPTION_LEVELS = {"Subtle", "Moderate", "Severe", "Catastrophic"};
    private static int currentCorruptionLevel = 0;

    private TensionManager() {}

    /**
     * Recompute all global metrics from substrate state across all server worlds.
     * Called once per tick from TensionServerTick after substrate integration.
     */
    public static void recompute(MinecraftServer server) {
        double sumT = 0.0;
        double sumTSq = 0.0;
        double maxT = 0.0;
        double sumDepth = 0.0;
        double maxDepth = 0.0;
        double sumMemory = 0.0;
        double sumActivation = 0.0;
        int activeCount = 0;
        int totalCount = 0;

        for (ServerWorld world : server.getWorlds()) {
            ChunkTensionData data = ChunkTensionData.getServerState(world);
            for (Map.Entry<ChunkPos, SubstrateState> entry : data.getSubstrateStatesView().entrySet()) {
                ChunkPos pos = entry.getKey();
                if (!world.isChunkLoaded(pos.x, pos.z)) continue;
                SubstrateState ss = entry.getValue();
                double t = SubstrateObservationModel.computeLocalTension(ss);
                sumT += t;
                sumTSq += t * t;
                if (t > maxT) maxT = t;
                double d = ss.getFieldDepth();
                sumDepth += d;
                if (d > maxDepth) maxDepth = d;
                sumMemory += ss.getMemory();
                sumActivation += ss.getActivation();
                if (t > SubstrateState.ACTIVE_TENSION_THRESHOLD) activeCount++;
                totalCount++;
            }
        }

        if (totalCount > 0) {
            globalMeanTension = sumT / totalCount;
            globalVariance = (sumTSq / totalCount) - (globalMeanTension * globalMeanTension);
            globalMeanDepth = sumDepth / totalCount;
            globalMeanMemory = sumMemory / totalCount;
            globalMeanActivation = sumActivation / totalCount;
            globalActiveFraction = (double) activeCount / totalCount;
        } else {
            globalMeanTension = 0.0;
            globalVariance = 0.0;
            globalMeanDepth = 0.0;
            globalMeanMemory = 0.0;
            globalMeanActivation = 0.0;
            globalActiveFraction = 0.0;
        }
        globalMaxTension = maxT;
        globalMaxDepth = maxDepth;

        // Storm flag driven by mean tension with hysteresis
        if (globalMeanTension > STORM_THRESHOLD && !stormActive) stormActive = true;
        if (globalMeanTension < STORM_HYSTERESIS) stormActive = false;

        updateCorruptionState();
    }

    /** Mean derived tension T(x) over all loaded substrate chunks. */
    public static double getTension() { return globalMeanTension; }
    public static double getMaxTension() { return globalMaxTension; }
    public static double getVariance() { return globalVariance; }
    public static double getActiveFraction() { return globalActiveFraction; }
    public static double getMeanDepth() { return globalMeanDepth; }
    public static double getMaxDepth() { return globalMaxDepth; }
    public static double getMeanMemory() { return globalMeanMemory; }
    public static double getMeanActivation() { return globalMeanActivation; }
    public static boolean isStormActive() { return stormActive; }

    public static int getCorruptionLevel() { return currentCorruptionLevel; }

    public static String getCorruptionLevelName() {
        return currentCorruptionLevel > 0
            ? CORRUPTION_LEVELS[Math.min(currentCorruptionLevel - 1, CORRUPTION_LEVELS.length - 1)]
            : "None";
    }

    private static void updateCorruptionState() {
        int newLevel = 0;
        for (int i = CORRUPTION_THRESHOLDS.length - 1; i >= 0; i--) {
            if (globalMeanTension >= CORRUPTION_THRESHOLDS[i]) {
                newLevel = i + 1;
                break;
            }
        }
        currentCorruptionLevel = newLevel;
    }

    // ---- Legacy compatibility stubs (kept for binary compatibility) ----

    /** @deprecated Global tension is now a derived metric; this is a no-op. */
    @Deprecated
    public static void setTension(double tension) {}

    /** @deprecated Global tension is now a derived metric; this is a no-op. */
    @Deprecated
    public static void addEvent(double amount) {}

    /** @deprecated Use ChunkTensionManager directly. */
    @Deprecated
    public static void addMiningTension(String blockId) {}

    /** @deprecated Use ChunkTensionManager directly. */
    @Deprecated
    public static void addMiningTension(String blockId, ServerPlayerEntity player) {
        if (player == null || player.getServer() == null) return;
        com.utdmod.tension.ChunkTensionManager.addMiningTension(
            player.getServerWorld(), new ChunkPos(player.getBlockPos()), blockId);
    }

    /** @deprecated Global tension is now a derived metric; this is a no-op. */
    @Deprecated
    public static void reduceTension(double amount) {}

    /** @deprecated Global tension is now a derived metric; this is a no-op. */
    @Deprecated
    public static void tickGlobalDynamics(double a, double b, long c, double d, double e) {}

    public static void triggerWraithEvent(World world) {}
    public static void triggerSerpentEvent(World world) {}

    public static TensionManager getServerState(MinecraftServer server) {
        return new TensionManager();
    }

    public float calculateBaseTension(ServerPlayerEntity player) {
        return 0.005f;
    }
}
