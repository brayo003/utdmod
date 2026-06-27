package com.utdmod.diag;

import java.util.List;

import com.utdmod.core.Region;
import com.utdmod.core.RegionState;
import com.utdmod.tension.ChunkTensionData;

import net.minecraft.util.math.ChunkPos;

/**
 * Terminal diagnostics (grep-friendly, bounded frequency from callers).
 */
public final class DiagnosticLogs {

    private static final double T1 = 0.95;
    private static final double T2 = 1.28;
    private static final double T3 = 3.0;
    private static final boolean DEBUG_PER_CHUNK_LOGS = Boolean.getBoolean("utd.diag.perChunk");
    private static final boolean DEBUG_GLOBAL_FLOW_LOGS = Boolean.getBoolean("utd.diag.globalFlow");

    private static String stateDisplayName(ChunkTensionData.ChunkState state) {
        if (state == ChunkTensionData.ChunkState.STABLE) {
            return "CALM";
        }
        return state.name();
    }

    /** Human-readable state for [EVENT_EFFECT] / HUD (STABLE → CALM). */
    public static String chunkStateLogName(ChunkTensionData.ChunkState state) {
        return stateDisplayName(state);
    }

    private DiagnosticLogs() {}

    private static void printSection(String name) {
        System.out.println("============================" + name + "============================");
    }

    public static boolean isPerChunkDebugEnabled() {
        return DEBUG_PER_CHUNK_LOGS;
    }

    public static boolean isGlobalFlowDebugEnabled() {
        return DEBUG_GLOBAL_FLOW_LOGS;
    }

    static String formatFieldSummaryLine(
        long tick,
        double globalTension,
        int regionCount,
        int largestRegionSize,
        int stormChunks,
        int fracturedChunks,
        double averageRegionMaturity,
        double totalRegionContribution
    ) {
        return String.format(
            "[FIELD]tick=%dGlobalT=%.4fRegions=%dLargestRegion=%dStormChunks=%dFracturedChunks=%dAverageRegionMaturity=%.4fTotalRegionContribution=%.4f",
            tick,
            globalTension,
            regionCount,
            largestRegionSize,
            stormChunks,
            fracturedChunks,
            averageRegionMaturity,
            totalRegionContribution
        );
    }

    static String formatRegionSummaryLine(int index, Region region) {
        return String.format(
            "[REGIONS]Region %dstate=%ssize=%dage=%daverageTension=%.4f maturity=%.4f contribution=%.4f fractured=%d storms=%d centroid=(%.1f,%.1f)",
            index,
            region.getState().name(),
            region.getChunkCount(),
            region.getAge(),
            region.getAverageTension(),
            region.getMaturity(),
            region.getContribution(),
            region.getFracturedChunks(),
            region.getStormChunks(),
            region.getCentroidX(),
            region.getCentroidZ()
        );
    }

    public static void fieldSummary(
        long tick,
        double globalTension,
        int regionCount,
        int largestRegionSize,
        int stormChunks,
        int fracturedChunks,
        double averageRegionMaturity,
        double totalRegionContribution
    ) {
        printSection("FIELD");
        System.out.println(formatFieldSummaryLine(
            tick,
            globalTension,
            regionCount,
            largestRegionSize,
            stormChunks,
            fracturedChunks,
            averageRegionMaturity,
            totalRegionContribution
        ));
        System.out.println();
    }

    public static void regionSummary(long tick, List<Region> regions) {
        if (regions == null || regions.isEmpty()) {
            return;
        }
        printSection("REGIONS");
        for (int i = 0; i < regions.size(); i++) {
            System.out.println(formatRegionSummaryLine(i + 1, regions.get(i)));
        }
        System.out.println();
    }

    public static void regionCreated(int id, int chunkCount, String state) {
        printSection("EVENTS");
        System.out.printf("[REGION_CREATED]id=%d chunks=%d state=%s%n", id, chunkCount, state);
    }

    public static void regionMerged(int oldId, int newId) {
        printSection("EVENTS");
        System.out.printf("[REGION_MERGED]old=%d new=%d%n", oldId, newId);
    }

    public static void regionDestroyed(int id, int chunkCount) {
        printSection("EVENTS");
        System.out.printf("[REGION_DESTROYED]id=%d chunks=%d%n", id, chunkCount);
    }

    public static void regionStateChanged(int id, String oldState, String newState) {
        printSection("EVENTS");
        System.out.printf("[REGION_STATE_CHANGE]Region %d %s -> %s%n", id, oldState, newState);
    }

    public static void globalLevelChanged(int oldLevel, int newLevel) {
        printSection("EVENTS");
        System.out.printf("[GLOBAL_LEVEL_CHANGE]%d -> %d%n", oldLevel, newLevel);
    }

    public static void playerEnterRegion(String playerName, int regionId, String state) {
        printSection("EVENTS");
        System.out.printf("[PLAYER_ENTER_REGION]player=%s region=%d state=%s%n", playerName, regionId, state);
    }

    public static void playerExitRegion(String playerName, int regionId) {
        printSection("EVENTS");
        System.out.printf("[PLAYER_EXIT_REGION]player=%s region=%d%n", playerName, regionId);
    }

    public static void ecologySummary(int regionId, RegionState state, int decorationsApplied, int recoveriesApplied) {
        printSection("ECOLOGY");
        System.out.printf("[ECOLOGY]Region=%d State=%s DecorationsApplied=%d RecoveriesApplied=%d%n", regionId, state.name(), decorationsApplied, recoveriesApplied);
    }

    public static String approximateState(double tension) {
        if (tension > T3) return "DECOUPLED";
        if (tension > T2) return "FRACTURED";
        if (tension > T1) return "STRAINED";
        return "CALM";
    }

    public static void tensionState(
        ChunkPos chunk,
        ChunkTensionData.ChunkState oldState,
        ChunkTensionData.ChunkState newState,
        double tension,
        long worldTick,
        String hysteresisNote
    ) {
        System.out.println("[TENSION_STATE]");
        System.out.printf("chunk=%d,%d%n", chunk.x, chunk.z);
        System.out.printf("old=%s%n", stateDisplayName(oldState));
        System.out.printf("new=%s%n", stateDisplayName(newState));
        System.out.printf("tension=%.5f%n", tension);
        System.out.printf("tick=%d%n", worldTick);
        if (hysteresisNote != null && !hysteresisNote.isEmpty()) {
            System.out.printf("hysteresis=%s%n", hysteresisNote);
        }
        System.out.println();
    }

    public static void region(
        long tick,
        int regionX,
        int regionZ,
        double avg,
        double max,
        int strained,
        int fractured,
        boolean storm,
        double hostileBias
    ) {
        System.out.println("[REGION]");
        System.out.printf("tick=%d%n", tick);
        System.out.printf("region=%d,%d%n", regionX, regionZ);
        System.out.printf("avg=%.4f%n", avg);
        System.out.printf("max=%.4f%n", max);
        System.out.printf("strained=%d%n", strained);
        System.out.printf("fractured=%d%n", fractured);
        System.out.printf("storm=%s%n", storm);
        System.out.printf("hostile_bias=%.4f%n", hostileBias);
        System.out.println();
    }

    /**
     * One line per second: explicit global budget terms (diagnostics only; dynamics unchanged).
     */
    public static void globalFlowLine(
        long tick,
        double globalAfter,
        double localCoupling,
        double ambientInflow,
        double ambientDecay,
        double nonlinearFeedback,
        double ritualBuffer,
        double stormDrive,
        double movementSum,
        double miningSum,
        double combatSum,
        double net
    ) {
        if (!DEBUG_GLOBAL_FLOW_LOGS) {
            return;
        }
        System.out.printf(
            "[GLOBAL_FLOW] tick=%d global=%.4f local_coupling=%.4f ambient_inflow=%.4f ambient_decay=%.4f "
                + "nonlinear_feedback=%.4f ritual_buffer=%.4f storm_drive=%.4f movement_sum=%.4f mining_sum=%.4f combat_sum=%.4f net=%.4f%n",
            tick,
            globalAfter,
            localCoupling,
            ambientInflow,
            ambientDecay,
            nonlinearFeedback,
            ritualBuffer,
            stormDrive,
            movementSum,
            miningSum,
            combatSum,
            net
        );
    }

    public static void eventEffect(
        String event,
        String blockOrDetail,
        ChunkPos chunk,
        double delta,
        double before,
        double after,
        boolean thresholdCrossed,
        String newState,
        long tick
    ) {
        System.out.println("[EVENT_EFFECT]");
        System.out.printf("event=%s%n", event);
        System.out.printf("block=%s%n", blockOrDetail != null ? blockOrDetail : "");
        System.out.printf("chunk=%d,%d%n", chunk.x, chunk.z);
        System.out.printf("delta=%.5f%n", delta);
        System.out.printf("before=%.5f%n", before);
        System.out.printf("after=%.5f%n", after);
        System.out.printf("threshold_crossed=%s%n", thresholdCrossed);
        System.out.printf("new_state=%s%n", newState);
        System.out.printf("tick=%d%n", tick);
        System.out.println();
    }

    public static void stormStart(
        long tick,
        String trigger,
        int regionX,
        int regionZ,
        double value,
        double global,
        int players,
        double stormStrength,
        int towardRx,
        int towardRz
    ) {
        System.out.println("[STORM]");
        System.out.printf("tick=%d%n", tick);
        System.out.printf("region=%d,%d%n", regionX, regionZ);
        System.out.printf("trigger=%s%n", trigger);
        System.out.printf("value=%.4f%n", value);
        System.out.printf("global=%.4f%n", global);
        System.out.printf("players=%d%n", players);
        System.out.printf("storm_strength=%.4f%n", stormStrength);
        System.out.printf("moving_toward=%d,%d%n", towardRx, towardRz);
        System.out.println();
    }

    public static void stormEnd(int durationTicks, double peak, int regionX, int regionZ, long tick) {
        System.out.println("[STORM_END]");
        System.out.printf("duration=%d%n", durationTicks);
        System.out.printf("peak=%.4f%n", peak);
        System.out.printf("region=%d,%d%n", regionX, regionZ);
        System.out.printf("tick=%d%n", tick);
        System.out.println();
    }

    public static void spawnEcology(int regionX, int regionZ, String state, double passiveModifier, double hostileModifier) {
        System.out.println("[SPAWN_ECOLOGY]");
        System.out.printf("region=%d,%d%n", regionX, regionZ);
        System.out.printf("state=%s%n", state);
        System.out.printf("passive_modifier=%.4f%n", passiveModifier);
        System.out.printf("hostile_modifier=%.4f%n", hostileModifier);
        System.out.println();
    }

    public static void recovery(ChunkPos chunk, double previousPeak, double current, double recoveryRate, long tick) {
        System.out.println("[RECOVERY]");
        System.out.printf("chunk=%d,%d%n", chunk.x, chunk.z);
        System.out.printf("previous_peak=%.4f%n", previousPeak);
        System.out.printf("current=%.4f%n", current);
        System.out.printf("recovery_rate=%.4f%n", recoveryRate);
        System.out.printf("tick=%d%n", tick);
        System.out.println();
    }

    public static void test1(String phase, ChunkPos chunk, long timeToStrained, long timeToFractured, double peak) {
        System.out.println("[TEST1]");
        System.out.printf("phase=%s%n", phase);
        System.out.printf("chunk=%d,%d%n", chunk.x, chunk.z);
        System.out.printf("time_to_strained=%d%n", timeToStrained);
        System.out.printf("time_to_fractured=%d%n", timeToFractured);
        System.out.printf("peak=%.4f%n", peak);
        System.out.println();
    }

    public static void test2(ChunkPos origin, int affectedChunks, int radius, double regionalAvg) {
        System.out.println("[TEST2]");
        System.out.printf("origin=%d,%d%n", origin.x, origin.z);
        System.out.printf("affected_chunks=%d%n", affectedChunks);
        System.out.printf("radius=%d%n", radius);
        System.out.printf("regional_avg=%.4f%n", regionalAvg);
        System.out.println();
    }

    public static void test3(ChunkPos chunk, double peak, long halfLifeTicks, long timeToCalm) {
        System.out.println("[TEST3]");
        System.out.printf("chunk=%d,%d%n", chunk.x, chunk.z);
        System.out.printf("peak=%.4f%n", peak);
        System.out.printf("half_life_ticks=%d%n", halfLifeTicks);
        System.out.printf("time_to_calm=%d%n", timeToCalm);
        System.out.println();
    }

    public static void test4(double perceived, int audioEvents, double overlay, int hostiles) {
        System.out.printf(
            "[TEST4] perceived=%.4f audio_events=%d overlay=%.4f hostiles=%d%n",
            perceived,
            audioEvents,
            overlay,
            hostiles
        );
    }
}
