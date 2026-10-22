package com.utdmod.experiment;

import com.utdmod.config.UTDConfig;
import com.utdmod.core.Region;
import com.utdmod.core.RegionManager;
import com.utdmod.interpretation.ChunkInterpretation;
import com.utdmod.interpretation.InterpretationService;
import com.utdmod.interpretation.SubstrateSemantics;
import com.utdmod.research.ResearchConfig;
import com.utdmod.research.SubstratePerturbation;
import com.utdmod.tension.ChunkTensionData;
import com.utdmod.tension.SubstrateState;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Structured experiment telemetry with causality chains, outcome tracking,
 * per-region summaries, and immutable experiment artifacts.
 */
public final class ExperimentLogger {
    private static final String LOG_ROOT = "logs/utd_experiments";
    private static final int SUBSTRATE_SNAPSHOT_PERIOD = 20;
    private static final int FIELD_SUMMARY_PERIOD = 1000;

    private static boolean initialized = false;
    private static BufferedWriter jsonlWriter;
    private static Path experimentDir;
    private static String runId;
    private static long startTimeMs;
    private static long startTick = 0;
    private static long worldSeed = -1;
    
    // Causality chain tracking
    private static final ThreadLocal<Long> currentChainId = ThreadLocal.withInitial(() -> -1L);
    private static long nextChainId = 1L;
    
    // Counters
    private static final Map<String, Long> actuatorCounts = new TreeMap<>();
    private static final Map<String, Long> actuatorSuccessCounts = new TreeMap<>();
    private static final Map<String, Long> actuatorFailureCounts = new TreeMap<>();
    private static final Map<String, Long> perturbationCounts = new TreeMap<>();
    private static final Map<String, Long> actuatorTimingNs = new TreeMap<>();
    private static final Map<ChunkPos, Long> recentChainByChunk = new HashMap<>();
    
    // Region statistics
    private static final Map<Integer, RegionStats> regionStats = new HashMap<>();

    private ExperimentLogger() {}

    /**
     * Container for per-region telemetry.
     */
    public static class RegionStats {
        public int regionId;
        public double sumActivation = 0.0;
        public double sumMemory = 0.0;
        public double sumCapacity = 0.0;
        public int sampleCount = 0;
        public int fracturedChunks = 0;
        public int loadedChunks = 0;
        public long perturbationCount = 0;
        public long actuatorFireCount = 0;
    }

    public static synchronized void initialize(MinecraftServer server) {
        if (initialized) {
            return;
        }
        initialized = true;
        runId = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss").withZone(ZoneId.systemDefault()).format(Instant.now());
        startTimeMs = System.currentTimeMillis();
        startTick = 0;

        experimentDir = Path.of(LOG_ROOT, runId);
        try {
            Files.createDirectories(experimentDir);
            jsonlWriter = Files.newBufferedWriter(
                experimentDir.resolve("experiment.jsonl"),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize experiment log", e);
        }

        Map<String, Object> startEvent = new LinkedHashMap<>();
        startEvent.put("event", "run_start");
        startEvent.put("run_id", runId);
        startEvent.put("start_time", Instant.ofEpochMilli(startTimeMs).toString());
        startEvent.put("git_commit", System.getProperty("git.commit.id", "unknown"));
        startEvent.put("minecraft_version", System.getProperty("minecraft.version", "unknown"));
        startEvent.put("utd_version", System.getProperty("utd.version", "dev"));
        startEvent.put("java_version", System.getProperty("java.version", "unknown"));
        startEvent.put("os_name", System.getProperty("os.name", "unknown"));
        worldSeed = getWorldSeed(server);
        startEvent.put("world_seed", worldSeed);
        startEvent.put("bot_seed", ResearchConfig.getBotSeed());
        startEvent.put("bot_type", ResearchConfig.getBotType());
        startEvent.put("configuration", buildConfigurationMap());
        startEvent.put("sampling_period_ticks", SUBSTRATE_SNAPSHOT_PERIOD);
        writeJsonLine(startEvent);
    }

    public static synchronized void ensureInitialized(MinecraftServer server) {
        if (initialized) {
            return;
        }
        initialize(server);
    }

    /**
     * Log actuator invocation with timing and outcome.
     */
    public static synchronized void logActuatorInvocation(
        long tick,
        ServerWorld world,
        ChunkPos chunk,
        String actuatorId,
        SubstrateState substrate,
        String decision,
        String reason,
        long executionTimeNs,
        ActuatorOutcome outcome
    ) {
        ensureInitialized(world != null ? world.getServer() : null);
        incrementActuatorCount(actuatorId);
        if (outcome == ActuatorOutcome.SUCCESS || outcome == ActuatorOutcome.NO_CHANGE) {
            incrementActuatorSuccess(actuatorId);
        } else if (outcome == ActuatorOutcome.FAILED) {
            incrementActuatorFailure(actuatorId);
        }
        actuatorTimingNs.merge(actuatorId, executionTimeNs, Long::sum);

        long chainId = getChainIdForChunk(chunk);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("event", "actuator_invocation");
        entry.put("tick", tick);
        entry.put("chain_id", chainId >= 0 ? chainId : null);
        entry.put("run_id", runId);
        entry.put("chunk", chunkToMap(chunk));
        entry.put("actuator", actuatorId);
        entry.put("activation", substrate.getActivation());
        entry.put("memory", substrate.getMemory());
        entry.put("capacity", substrate.getCapacity());
        entry.put("readiness", substrate.getReadiness());
        entry.put("depth", substrate.getFieldDepth());
        entry.put("intent", decision != null ? decision : "EXECUTED");
        entry.put("reason", reason != null ? reason : "");
        entry.put("observed_outcome", outcome.name());
        entry.put("execution_time_ns", executionTimeNs);
        writeJsonLine(entry);
    }

    /**
     * Log a world-scoped actuator decision (no single chunk or substrate snapshot).
     * Used by coordinators that aggregate many chunks into one global effect (e.g. weather).
     */
    public static synchronized void logGlobalDecision(
        long tick,
        ServerWorld world,
        String actuatorId,
        String decision,
        String reason,
        String effect,
        ActuatorOutcome outcome
    ) {
        ensureInitialized(world != null ? world.getServer() : null);
        incrementActuatorCount(actuatorId);
        if (outcome == ActuatorOutcome.SUCCESS) {
            incrementActuatorSuccess(actuatorId);
        } else if (outcome == ActuatorOutcome.FAILED) {
            incrementActuatorFailure(actuatorId);
        }

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("event", "actuator_decision");
        entry.put("tick", tick);
        entry.put("chain_id", null);
        entry.put("run_id", runId);
        entry.put("scope", "world");
        if (world != null) {
            entry.put("dimension", world.getRegistryKey().getValue().toString());
        }
        entry.put("chunk", chunkToMap(null));
        entry.put("actuator", actuatorId);
        entry.put("intent", decision != null ? decision : "UNKNOWN");
        entry.put("reason", reason != null ? reason : "");
        entry.put("effect", effect != null ? effect : "");
        entry.put("observed_outcome", outcome.name());
        writeJsonLine(entry);
    }

    /**
     * Log detailed actuator decision with block/position effects.
     */
    public static synchronized void logActuatorDecision(
        long tick,
        ServerWorld world,
        ChunkPos chunk,
        String actuatorId,
        SubstrateState substrate,
        String decision,
        String reason,
        String effect,
        String block,
        BlockPos position,
        long executionTimeNs,
        ActuatorOutcome outcome
    ) {
        if (chunk == null) {
            logGlobalDecision(tick, world, actuatorId, decision, reason, effect, outcome);
            return;
        }
        ensureInitialized(world != null ? world.getServer() : null);
        incrementActuatorCount(actuatorId);
        if (outcome == ActuatorOutcome.SUCCESS) {
            incrementActuatorSuccess(actuatorId);
        } else if (outcome == ActuatorOutcome.FAILED) {
            incrementActuatorFailure(actuatorId);
        }
        actuatorTimingNs.merge(actuatorId, executionTimeNs, Long::sum);

        long chainId = getChainIdForChunk(chunk);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("event", "actuator_decision");
        entry.put("tick", tick);
        entry.put("chain_id", chainId >= 0 ? chainId : null);
        entry.put("run_id", runId);
        entry.put("chunk", chunkToMap(chunk));
        entry.put("actuator", actuatorId);
        if (substrate != null) {
            entry.put("activation", substrate.getActivation());
            entry.put("memory", substrate.getMemory());
            entry.put("capacity", substrate.getCapacity());
            entry.put("readiness", substrate.getReadiness());
            entry.put("depth", substrate.getFieldDepth());
        }
        entry.put("intent", decision != null ? decision : "UNKNOWN");
        entry.put("reason", reason != null ? reason : "");
        entry.put("effect", effect != null ? effect : "");
        entry.put("block", block != null ? block : "");
        entry.put("position", position != null ? blockPosToMap(position) : null);
        entry.put("observed_outcome", outcome.name());
        entry.put("execution_time_ns", executionTimeNs);
        writeJsonLine(entry);
    }

    /**
     * Log substrate state snapshot (periodic).
     */
    public static synchronized void logSubstrateSnapshot(long tick, ServerWorld world, ChunkPos chunk, SubstrateState substrate) {
        logSubstrateSnapshot(tick, world, chunk, substrate, false);
    }

    public static synchronized void logSubstrateSnapshot(long tick, ServerWorld world, ChunkPos chunk, SubstrateState substrate, boolean force) {
        if (!force && tick % SUBSTRATE_SNAPSHOT_PERIOD != 0) {
            return;
        }
        ensureInitialized(world != null ? world.getServer() : null);
        
        long chainId = getChainIdForChunk(chunk);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("event", "substrate_snapshot");
        entry.put("tick", tick);
        entry.put("chain_id", chainId >= 0 ? chainId : null);
        entry.put("run_id", runId);
        entry.put("chunk", chunkToMap(chunk));
        entry.put("activation", substrate.getActivation());
        entry.put("memory", substrate.getMemory());
        entry.put("transport", substrate.getTransport());
        entry.put("field_depth", substrate.getFieldDepth());
        entry.put("readiness", substrate.getReadiness());
        entry.put("radius", substrate.getRadius());
        entry.put("capacity", substrate.getCapacity());
        entry.put("peak_activation", substrate.getPeakActivation());
        entry.put("convergence", substrate.getConvergence());
        entry.put("baseline", substrate.getBaseline());
        entry.put("external_input", substrate.getExternalInput());
        writeJsonLine(entry);
    }

    /**
     * Log interpretation semantics and primary intent for a chunk.
     */
    public static synchronized void logInterpretationSnapshot(long tick, ServerWorld world, ChunkInterpretation interpretation) {
        if (tick % SUBSTRATE_SNAPSHOT_PERIOD != 0) {
            return;
        }
        ensureInitialized(world != null ? world.getServer() : null);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("event", "interpretation_snapshot");
        entry.put("tick", tick);
        entry.put("run_id", runId);
        entry.put("chunk", chunkToMap(interpretation.chunkPos));
        var semantics = interpretation.semantics;
        entry.put("ecological_stress", semantics.ecologicalStress);
        entry.put("environmental_stability", semantics.environmentalStability);
        entry.put("migration_pressure", semantics.migrationPressure);
        entry.put("corridor_strength", semantics.corridorStrength);
        entry.put("prosperity_potential", semantics.prosperityPotential);
        entry.put("recovery_state", semantics.recoveryState);
        entry.put("collapse_tendency", semantics.collapseTendency);
        entry.put("local_gradient", semantics.localGradient);
        entry.put("local_density", semantics.localDensity);
        entry.put("transport_bias_x", semantics.transportBiasX);
        entry.put("transport_bias_z", semantics.transportBiasZ);
        if (interpretation.primaryIntent != null) {
            entry.put("intent", interpretation.primaryIntent.type.name());
            entry.put("intent_confidence", interpretation.primaryIntent.confidence);
            entry.put("intent_persistence", interpretation.primaryIntent.persistenceTicks);
            entry.put("intent_reason", interpretation.primaryIntent.reason);
        }
        writeJsonLine(entry);
    }

    /**
     * Log perturbation event (player/bot action).
     */
    public static synchronized void logPerturbationEvent(ServerWorld world, SubstratePerturbation perturbation) {
        ensureInitialized(world != null ? world.getServer() : null);
        String type = perturbation.type != null ? perturbation.type.name() : "UNKNOWN";
        incrementPerturbationCount(type);

        long chainId = currentChainId.get();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("event", "perturbation");
        entry.put("tick", perturbation.tick);
        entry.put("chain_id", chainId >= 0 ? chainId : null);
        entry.put("run_id", runId);
        entry.put("world", perturbation.worldId);
        entry.put("dimension", perturbation.dimension);
        entry.put("chunk", chunkToMap(perturbation.chunk));
        entry.put("position", blockPosToMap(perturbation.blockPos));
        entry.put("player", perturbation.playerName);
        entry.put("perturbation_type", type);
        entry.put("detail", perturbation.detail);
        entry.put("magnitude", perturbation.magnitude);
        writeJsonLine(entry);
        if (perturbation.chunk != null && chainId >= 0) {
            registerChainForChunk(perturbation.chunk, chainId);
        }
        if (world != null && perturbation.chunk != null) {
            ChunkTensionData tensionData = ChunkTensionData.getServerState(world);
            SubstrateState substrate = tensionData.getOrCreateSubstrateState(perturbation.chunk);
            logSubstrateSnapshot(perturbation.tick, world, perturbation.chunk, substrate, true);
        }
    }

    /**
     * Log field-level summary with per-region breakdown.
     */
    public static synchronized void maybeLogFieldSummary(MinecraftServer server, long tick) {
        if (tick % FIELD_SUMMARY_PERIOD != 0) {
            return;
        }
        ensureInitialized(server);
        
        List<Region> regions = RegionManager.getRegions();
        int stable = 0, strained = 0, fractured = 0, recovering = 0;
        Map<Integer, RegionStats> regionData = new HashMap<>();
        
        for (Region region : regions) {
            RegionStats stats = new RegionStats();
            stats.regionId = region.getId();
            
            switch (region.getState()) {
                case DORMANT -> stable++;
                case GROWING -> recovering++;
                case STRAINED -> strained++;
                case FRACTURED -> fractured++;
                case ANCIENT -> stable++;
            }
            
            regionData.put(stats.regionId, stats);
        }

        double sumActivation = 0.0, sumMemory = 0.0, sumCapacity = 0.0, sumReadiness = 0.0, sumSqActivation = 0.0;
        int sampleCount = 0;
        
        for (ServerWorld world : server.getWorlds()) {
            ChunkTensionData data = ChunkTensionData.getServerState(world);
            for (ChunkPos chunkPos : data.getActiveChunkPositions()) {
                if (!world.isChunkLoaded(chunkPos.x, chunkPos.z)) continue;
                SubstrateState substrate = data.getSubstrateState(chunkPos);
                if (substrate == null) continue;
                double activation = substrate.getActivation();
                sumActivation += activation;
                sumSqActivation += activation * activation;
                sumMemory += substrate.getMemory();
                sumCapacity += substrate.getCapacity();
                sumReadiness += substrate.getReadiness();
                sampleCount++;
            }
        }
        
        double meanActivation = sampleCount > 0 ? sumActivation / sampleCount : 0.0;
        double stdActivation = sampleCount > 1 
            ? Math.sqrt((sumSqActivation - (sumActivation * sumActivation / sampleCount)) / (sampleCount - 1)) 
            : 0.0;
        
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("event", "field_summary");
        entry.put("tick", tick);
        entry.put("run_id", runId);
        entry.put("region_count", regions.size());
        entry.put("stable_regions", stable);
        entry.put("strained_regions", strained);
        entry.put("fractured_regions", fractured);
        entry.put("recovering_regions", recovering);
        entry.put("activation_mean", meanActivation);
        entry.put("activation_std", stdActivation);
        entry.put("memory_mean", sampleCount > 0 ? sumMemory / sampleCount : 0.0);
        entry.put("capacity_mean", sampleCount > 0 ? sumCapacity / sampleCount : 0.0);
        entry.put("readiness_mean", sampleCount > 0 ? sumReadiness / sampleCount : 0.0);
        entry.put("actuator_counts", new LinkedHashMap<>(actuatorCounts));
        entry.put("actuator_success_counts", new LinkedHashMap<>(actuatorSuccessCounts));
        entry.put("actuator_failure_counts", new LinkedHashMap<>(actuatorFailureCounts));
        entry.put("perturbation_counts", new LinkedHashMap<>(perturbationCounts));

        double sumStress = 0.0, sumStability = 0.0, sumCorridor = 0.0;
        int corridorChunks = 0, settlementChunks = 0, intentSamples = 0;
        Map<String, Integer> intentCounts = new TreeMap<>();
        for (ServerWorld world : server.getWorlds()) {
            for (ChunkInterpretation interpretation : InterpretationService.latestForWorld(world).values()) {
                SubstrateSemantics semantics = interpretation.semantics;
                sumStress += semantics.ecologicalStress;
                sumStability += semantics.environmentalStability;
                sumCorridor += semantics.corridorStrength;
                if (semantics.corridorStrength > 0.008) {
                    corridorChunks++;
                }
                if (semantics.settlementTendency > 0.55) {
                    settlementChunks++;
                }
                if (interpretation.primaryIntent != null) {
                    intentCounts.merge(interpretation.primaryIntent.type.name(), 1, Integer::sum);
                }
                intentSamples++;
            }
        }
        if (intentSamples > 0) {
            entry.put("interpretation_mean_stress", sumStress / intentSamples);
            entry.put("interpretation_mean_stability", sumStability / intentSamples);
            entry.put("interpretation_mean_corridor", sumCorridor / intentSamples);
            entry.put("interpretation_corridor_chunks", corridorChunks);
            entry.put("interpretation_settlement_chunks", settlementChunks);
            entry.put("interpretation_intent_counts", intentCounts);
        }

        entry.put("top_unstable_regions", topUnstableRegionCoordinates(regions, 3));
        
        // Per-region breakdowns
        List<Map<String, Object>> regionList = new ArrayList<>();
        for (Region region : regions) {
            RegionStats stats = regionData.get(region.getId());
            if (stats != null) {
                Map<String, Object> regionEntry = new LinkedHashMap<>();
                regionEntry.put("region_id", region.getId());
                regionEntry.put("centroid", new LinkedHashMap<>() {{
                    put("x", region.getCentroidX());
                    put("z", region.getCentroidZ());
                }});
                regionEntry.put("chunk_count", region.getChunkCount());
                regionEntry.put("state", region.getState().name());
                // Full substrate statistics — tension is a derived metric
                regionEntry.put("mean_tension", region.getMeanTension());
                regionEntry.put("max_tension", region.getMaxTension());
                regionEntry.put("variance_tension", region.getVarianceTension());
                regionEntry.put("active_fraction", region.getActiveFraction());
                regionEntry.put("mean_depth", region.getMeanDepth());
                regionEntry.put("mean_memory", region.getMeanMemory());
                regionEntry.put("mean_activation", region.getMeanActivation());
                regionEntry.put("max_activation", region.getMaxActivation());
                regionEntry.put("average_maturity", region.getMaturity());
                regionEntry.put("fractured_chunks", region.getFracturedChunks());
                regionEntry.put("storm_chunks", region.getStormChunks());
                regionList.add(regionEntry);
            }
        }
        entry.put("regions", regionList);
        
        writeJsonLine(entry);
    }

    /**
     * Start a causality chain. Returns the chain ID.
     */
    public static synchronized long startCausalityChain() {
        long chainId = nextChainId++;
        currentChainId.set(chainId);
        return chainId;
    }

    /**
     * End causality chain and return to default state.
     */
    public static synchronized void endCausalityChain() {
        currentChainId.set(-1L);
    }

    private static synchronized void registerChainForChunk(ChunkPos chunk, long chainId) {
        if (chunk != null && chainId >= 0) {
            recentChainByChunk.put(chunk, chainId);
        }
    }

    private static synchronized long getChainIdForChunk(ChunkPos chunk) {
        long chainId = currentChainId.get();
        if (chainId >= 0) {
            return chainId;
        }
        if (chunk == null) {
            return -1L;
        }
        return recentChainByChunk.getOrDefault(chunk, -1L);
    }

    /**
     * Set the current chain ID (for propagating chains through callbacks).
     */
    public static synchronized void setCurrentChain(long chainId) {
        currentChainId.set(chainId);
    }

    /**
     * Log the conclusion of the entire run.
     */
    public static synchronized void logRunEnd(MinecraftServer server, long finalTick) {
        logRunEnd(server, finalTick, "completed");
    }

    public static synchronized void logRunEnd(MinecraftServer server, long finalTick, String status) {
        ensureInitialized(server);
        long endTimeMs = System.currentTimeMillis();
        long runtimeMs = endTimeMs - startTimeMs;
        double runtimeSec = runtimeMs / 1000.0;

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("event", "run_end");
        entry.put("run_id", runId);
        entry.put("end_time", Instant.ofEpochMilli(endTimeMs).toString());
        entry.put("runtime_ms", runtimeMs);
        entry.put("runtime_seconds", runtimeSec);
        entry.put("final_tick", finalTick);
        entry.put("total_perturbations", perturbationCounts.values().stream().mapToLong(Long::longValue).sum());
        entry.put("total_actuator_invocations", actuatorCounts.values().stream().mapToLong(Long::longValue).sum());
        entry.put("total_actuator_successes", actuatorSuccessCounts.values().stream().mapToLong(Long::longValue).sum());
        entry.put("total_actuator_failures", actuatorFailureCounts.values().stream().mapToLong(Long::longValue).sum());
        entry.put("status", status);
        entry.put("actuator_counts", new LinkedHashMap<>(actuatorCounts));
        entry.put("actuator_success_counts", new LinkedHashMap<>(actuatorSuccessCounts));
        entry.put("actuator_failure_counts", new LinkedHashMap<>(actuatorFailureCounts));
        entry.put("actuator_timing_avg_ns", computeActuatorAverages());
        entry.put("perturbation_counts", new LinkedHashMap<>(perturbationCounts));
        writeJsonLine(entry);
        
        // Flush and close JSONL writer
        try {
            if (jsonlWriter != null) {
                jsonlWriter.flush();
                jsonlWriter.close();
            }
        } catch (IOException e) {
            System.err.println("Failed to close JSONL writer: " + e.getMessage());
        }
        
        // Export metadata, summaries, and CSV files
        exportMetadata();
        exportConfiguration();
        exportBuildInfo();
        exportManifest(finalTick, runtimeSec);
        exportCoverSheet(server, finalTick, runtimeSec, status);
        exportSummary(finalTick, runtimeSec);
        exportActuatorCounts();
        exportPerturbationCounts();
        exportRegionSummary();
    }

    private static void exportCoverSheet(MinecraftServer server, long finalTick, double runtimeSec, String status) {
        try {
            int regionCount = RegionManager.getRegions().size();
            long totalPerturbations = perturbationCounts.values().stream().mapToLong(Long::longValue).sum();
            long weatherEvents = actuatorCounts.getOrDefault("weather", 0L);
            long villageEvents = actuatorCounts.getOrDefault("village", 0L);
            long cropEvents = actuatorCounts.getOrDefault("resource_regeneration", 0L);
            long mobEvents = actuatorCounts.getOrDefault("mob_behavior", 0L);

            double averageActivation = 0.0;
            double peakActivation = 0.0;
            double peakReadiness = 0.0;
            double maxDepth = 0.0;
            long sampleCount = 0;
            for (ServerWorld world : server.getWorlds()) {
                ChunkTensionData data = ChunkTensionData.getServerState(world);
                if (data == null) {
                    continue;
                }
                for (ChunkPos chunk : data.getActiveChunkPositions()) {
                    SubstrateState substrate = data.getSubstrateState(chunk);
                    if (substrate == null) continue;
                    double activation = substrate.getActivation();
                    double readiness = substrate.getReadiness();
                    double depth = substrate.getFieldDepth();
                    averageActivation += activation;
                    peakActivation = Math.max(peakActivation, activation);
                    peakReadiness = Math.max(peakReadiness, readiness);
                    maxDepth = Math.max(maxDepth, depth);
                    sampleCount++;
                }
            }
            if (sampleCount > 0) {
                averageActivation /= sampleCount;
            }

            StringBuilder cover = new StringBuilder();
            cover.append("Run ID: ").append(runId).append('\n');
            cover.append("World seed: ").append(worldSeed).append('\n');
            cover.append("Bot seed: ").append(ResearchConfig.getBotSeed()).append('\n');
            cover.append("Duration (ticks): ").append(finalTick).append('\n');
            cover.append("Perturbations: ").append(totalPerturbations).append('\n');
            cover.append("Regions formed: ").append(regionCount).append('\n');
            cover.append("Average activation: ").append(String.format("%.4f", averageActivation)).append('\n');
            cover.append("Peak activation: ").append(String.format("%.4f", peakActivation)).append('\n');
            cover.append("Peak readiness: ").append(String.format("%.4f", peakReadiness)).append('\n');
            cover.append("Maximum depth: ").append(String.format("%.4f", maxDepth)).append('\n');
            cover.append("Weather events: ").append(weatherEvents).append('\n');
            cover.append("Village interventions: ").append(villageEvents).append('\n');
            cover.append("Crop interventions: ").append(cropEvents).append('\n');
            cover.append("Mob interventions: ").append(mobEvents).append('\n');
            cover.append("Threshold crossings: not recorded\n");
            // NOTE: "Determinism" previously reflected whether the run ended cleanly,
            // not whether the substrate field reproduced bit-for-bit across runs.
            // The substrate integration (A/M/D/B via FieldDerivative.computeUnified) is
            // deterministic given fixed player inputs and chunk-loading sequence.
            // Known inter-run variation sources (do NOT affect substrate field values):
            //   - ecology block mutations: world.random (RegionEcologyManager)
            //   - TTL pruning decision: System.currentTimeMillis() (ChunkTensionData)
            //   - world/player list iteration order: varies with Minecraft chunk loading
            cover.append("Run completed cleanly: ").append("completed".equals(status) ? "YES" : "NO").append('\n');
            cover.append("Determinism: NOT_MEASURED\n");
            cover.append("Determinism note: substrate field (A/M/D/B) is deterministic given fixed inputs; ")
                 .append("ecology block mutations and TTL pruning use wall-clock time and world.random.\n");
            cover.append("Warnings: none\n");

            Path coverFile = experimentDir.resolve("cover_sheet.txt");
            Files.writeString(coverFile, cover.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to export cover sheet: " + e.getMessage());
        }
    }

    private static void exportMetadata() {
        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("run_id", runId);
            metadata.put("start_time", Instant.ofEpochMilli(startTimeMs).toString());
            metadata.put("configuration", buildConfigurationMap());
            metadata.put("bot_seed", ResearchConfig.getBotSeed());
            metadata.put("bot_type", ResearchConfig.getBotType());
            
            Path metadataFile = experimentDir.resolve("metadata.json");
            Files.writeString(metadataFile, toJsonPretty(metadata), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to export metadata: " + e.getMessage());
        }
    }

    private static void exportSummary(long finalTick, double runtimeSec) {
        try {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("run_id", runId);
            summary.put("runtime_seconds", runtimeSec);
            summary.put("final_tick", finalTick);
            summary.put("total_perturbations", perturbationCounts.values().stream().mapToLong(Long::longValue).sum());
            summary.put("total_actuator_invocations", actuatorCounts.values().stream().mapToLong(Long::longValue).sum());
            Map<String, Object> breakdown = new LinkedHashMap<>();
            breakdown.put("successes", actuatorSuccessCounts.values().stream().mapToLong(Long::longValue).sum());
            breakdown.put("failures", actuatorFailureCounts.values().stream().mapToLong(Long::longValue).sum());
            summary.put("actuator_breakdown", breakdown);
            summary.put("top_actuators", topActuators(5));
            summary.put("top_perturbations", topPerturbations(5));
            
            Path summaryFile = experimentDir.resolve("summary.json");
            Files.writeString(summaryFile, toJsonPretty(summary), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to export summary: " + e.getMessage());
        }
    }

    private static void exportConfiguration() {
        try {
            Path configurationFile = experimentDir.resolve("configuration.json");
            Files.writeString(configurationFile, toJsonPretty(buildConfigurationMap()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to export configuration: " + e.getMessage());
        }
    }

    private static void exportBuildInfo() {
        try {
            Map<String, Object> buildInfo = new LinkedHashMap<>();
            buildInfo.put("run_id", runId);
            buildInfo.put("minecraft_version", System.getProperty("minecraft.version", "unknown"));
            buildInfo.put("java_version", System.getProperty("java.version", "unknown"));
            buildInfo.put("os_name", System.getProperty("os.name", "unknown"));
            buildInfo.put("fabric_loader_version", getModVersion("fabricloader"));
            buildInfo.put("fabric_api_version", getModVersion("fabric-api"));
            buildInfo.put("utd_version", System.getProperty("utd.version", "dev"));
            buildInfo.put("git_commit", System.getProperty("git.commit.id", "unknown"));
            buildInfo.put("mappings", System.getProperty("mappings", "unknown"));
            Path buildFile = experimentDir.resolve("build.txt");
            Files.writeString(buildFile, toJsonPretty(buildInfo), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to export build info: " + e.getMessage());
        }
    }

    private static void exportManifest(long finalTick, double runtimeSec) {
        try {
            StringBuilder manifest = new StringBuilder();
            manifest.append("run_id: ").append(runId).append('\n');
            manifest.append("start_time: ").append(Instant.ofEpochMilli(startTimeMs).toString()).append('\n');
            manifest.append("end_time: ").append(Instant.ofEpochMilli(startTimeMs + (long) (runtimeSec * 1000)).toString()).append('\n');
            manifest.append("runtime_seconds: ").append(runtimeSec).append('\n');
            manifest.append("final_tick: ").append(finalTick).append('\n');
            manifest.append("minecraft_version: ").append(System.getProperty("minecraft.version", "unknown")).append('\n');
            manifest.append("java_version: ").append(System.getProperty("java.version", "unknown")).append('\n');
            manifest.append("fabric_loader_version: ").append(getModVersion("fabricloader")).append('\n');
            manifest.append("fabric_api_version: ").append(getModVersion("fabric-api")).append('\n');
            manifest.append("utd_version: ").append(System.getProperty("utd.version", "dev")).append('\n');
            manifest.append("git_commit: ").append(System.getProperty("git.commit.id", "unknown")).append('\n');
            manifest.append("world_seed: ").append(getWorldSeed(null)).append('\n');
            manifest.append("bot_seed: ").append(ResearchConfig.getBotSeed()).append('\n');
            manifest.append("bot_type: ").append(ResearchConfig.getBotType()).append('\n');
            Path manifestFile = experimentDir.resolve("manifest.txt");
            Files.writeString(manifestFile, manifest.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to export manifest: " + e.getMessage());
        }
    }

    private static String getModVersion(String modId) {
        try {
            Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(modId);
            return container.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private static void exportActuatorCounts() {
        try {
            StringBuilder csv = new StringBuilder();
            csv.append("actuator,invocations,successes,failures,avg_time_ns\n");
            for (String actuator : actuatorCounts.keySet()) {
                long invocations = actuatorCounts.get(actuator);
                long successes = actuatorSuccessCounts.getOrDefault(actuator, 0L);
                long failures = actuatorFailureCounts.getOrDefault(actuator, 0L);
                long totalTimeNs = actuatorTimingNs.getOrDefault(actuator, 0L);
                long avgTimeNs = invocations > 0 ? totalTimeNs / invocations : 0;
                csv.append(String.format("%s,%d,%d,%d,%d\n", actuator, invocations, successes, failures, avgTimeNs));
            }
            Path csvFile = experimentDir.resolve("actuator_counts.csv");
            Files.writeString(csvFile, csv.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to export actuator counts: " + e.getMessage());
        }
    }

    private static void exportPerturbationCounts() {
        try {
            StringBuilder csv = new StringBuilder();
            csv.append("perturbation_type,count\n");
            for (Map.Entry<String, Long> e : perturbationCounts.entrySet()) {
                csv.append(String.format("%s,%d\n", e.getKey(), e.getValue()));
            }
            Path csvFile = experimentDir.resolve("perturbations.csv");
            Files.writeString(csvFile, csv.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to export perturbation counts: " + e.getMessage());
        }
    }

    private static void exportRegionSummary() {
        try {
            StringBuilder csv = new StringBuilder();
            csv.append("region_id,chunk_count,state,mean_tension,max_tension,variance_tension,active_fraction,mean_depth,mean_memory,mean_activation,max_activation,fractured_chunks,storm_chunks\n");
            List<Region> regions = RegionManager.getRegions();
            for (Region region : regions) {
                csv.append(String.format("%d,%d,%s,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%d,%d\n",
                    region.getId(),
                    region.getChunkCount(),
                    region.getState().name(),
                    region.getMeanTension(),
                    region.getMaxTension(),
                    region.getVarianceTension(),
                    region.getActiveFraction(),
                    region.getMeanDepth(),
                    region.getMeanMemory(),
                    region.getMeanActivation(),
                    region.getMaxActivation(),
                    region.getFracturedChunks(),
                    region.getStormChunks()
                ));
            }
            Path csvFile = experimentDir.resolve("region_summary.csv");
            Files.writeString(csvFile, csv.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to export region summary: " + e.getMessage());
        }
    }

    private static Object topUnstableRegionCoordinates(List<Region> regions, int limit) {
        return regions.stream()
            .sorted((a, b) -> Double.compare(b.getAverageTension(), a.getAverageTension()))
            .limit(limit)
            .map(region -> chunkToMap(new ChunkPos((int) Math.floor(region.getCentroidX()), (int) Math.floor(region.getCentroidZ()))))
            .toList();
    }

    private static List<Map<String, Object>> topActuators(int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        actuatorCounts.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .limit(limit)
            .forEach(e -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("actuator", e.getKey());
                entry.put("invocations", e.getValue());
                entry.put("successes", actuatorSuccessCounts.getOrDefault(e.getKey(), 0L));
                result.add(entry);
            });
        return result;
    }

    private static List<Map<String, Object>> topPerturbations(int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        perturbationCounts.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .limit(limit)
            .forEach(e -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("type", e.getKey());
                entry.put("count", e.getValue());
                result.add(entry);
            });
        return result;
    }

    private static Map<String, Object> computeActuatorAverages() {
        Map<String, Object> averages = new LinkedHashMap<>();
        for (String actuator : actuatorCounts.keySet()) {
            long count = actuatorCounts.get(actuator);
            long totalTimeNs = actuatorTimingNs.getOrDefault(actuator, 0L);
            long avgTimeNs = count > 0 ? totalTimeNs / count : 0;
            averages.put(actuator, avgTimeNs);
        }
        return averages;
    }

    private static void incrementActuatorCount(String actuatorId) {
        actuatorCounts.merge(actuatorId, 1L, Long::sum);
    }

    private static void incrementActuatorSuccess(String actuatorId) {
        actuatorSuccessCounts.merge(actuatorId, 1L, Long::sum);
    }

    private static void incrementActuatorFailure(String actuatorId) {
        actuatorFailureCounts.merge(actuatorId, 1L, Long::sum);
    }

    private static void incrementPerturbationCount(String type) {
        perturbationCounts.merge(type, 1L, Long::sum);
    }

    private static long getWorldSeed(MinecraftServer server) {
        if (server == null) {
            return -1;
        }
        try {
            ServerWorld overworld = server.getOverworld();
            return overworld != null ? overworld.getSeed() : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static Map<String, Object> buildConfigurationMap() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("substrate_transport_gain", UTDConfig.substrateTransportGain);
        config.put("substrate_activation_decay", UTDConfig.substrateActivationDecay);
        config.put("substrate_memory_gain", UTDConfig.substrateMemoryGain);
        config.put("substrate_memory_decay", UTDConfig.substrateMemoryDecay);
        config.put("substrate_memory_influence", UTDConfig.substrateMemoryInfluence);
        config.put("substrate_readiness_scale", UTDConfig.substrateReadinessScale);
        config.put("substrate_transport_distance_decay", UTDConfig.substrateTransportDistanceDecay);
        config.put("substrate_memory_effect_k", UTDConfig.substrateMemoryEffectK);
        config.put("substrate_local_tension_bias", UTDConfig.substrateLocalTensionBias);
        config.put("substrate_field_depth_gain", UTDConfig.substrateFieldDepthGain);
        config.put("substrate_field_depth_decay", UTDConfig.substrateFieldDepthDecay);
        config.put("substrate_field_depth_radius", UTDConfig.substrateFieldDepthRadius);
        config.put("substrate_field_depth_effect_k", UTDConfig.substrateFieldDepthEffectK);
        config.put("configured_substrate_clamp_max", UTDConfig.substrateClampMax);
        config.put("effective_v2_clamp_max", com.utdmod.tension.ParameterSet.runtime().v2ClampMax);
        config.put("substrate_gameplay_tuning_enabled", UTDConfig.substrateGameplayTuningEnabled);
        config.put("substrate_field_depth_readiness_scale", UTDConfig.substrateFieldDepthReadinessScale);
        return config;
    }

    private static void writeJsonLine(Map<String, Object> entry) {
        if (!initialized || jsonlWriter == null) {
            return;
        }
        try {
            jsonlWriter.write(toJson(entry));
            jsonlWriter.newLine();
            jsonlWriter.flush();
        } catch (IOException e) {
            System.err.println("Failed to write experiment JSON line: " + e.getMessage());
        }
    }

    private static String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof String) {
            return quote((String) value);
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder();
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(quote(entry.getKey().toString()));
                sb.append(':');
                sb.append(toJson(entry.getValue()));
            }
            sb.append('}');
            return sb.toString();
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first) sb.append(',');
                first = false;
                sb.append(toJson(item));
            }
            sb.append(']');
            return sb.toString();
        }
        return quote(value.toString());
    }

    private static String toJsonPretty(Object value) {
        return toJsonPrettyIndent(value, 0);
    }

    private static String toJsonPrettyIndent(Object value, int indent) {
        String indentStr = "  ".repeat(indent);
        String nextIndentStr = "  ".repeat(indent + 1);
        
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof String) {
            return quote((String) value);
        }
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) return "{}";
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",\n");
                first = false;
                sb.append(nextIndentStr).append(quote(entry.getKey().toString())).append(": ");
                sb.append(toJsonPrettyIndent(entry.getValue(), indent + 1));
            }
            sb.append("\n").append(indentStr).append("}");
            return sb.toString();
        }
        if (value instanceof Iterable<?> iterable) {
            List<?> list;
            if (iterable instanceof List<?> l) {
                list = l;
            } else {
                List<Object> tmp = new ArrayList<>();
                for (Object item : iterable) {
                    tmp.add(item);
                }
                list = tmp;
            }
            if (list.isEmpty()) return "[]";
            StringBuilder sb = new StringBuilder();
            sb.append("[\n");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(",\n");
                first = false;
                sb.append(nextIndentStr).append(toJsonPrettyIndent(item, indent + 1));
            }
            sb.append("\n").append(indentStr).append("]");
            return sb.toString();
        }
        return quote(value.toString());
    }

    private static Map<String, Object> chunkToMap(ChunkPos chunk) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (chunk == null) {
            map.put("scope", "world");
            return map;
        }
        map.put("x", chunk.x);
        map.put("z", chunk.z);
        return map;
    }

    private static Map<String, Object> blockPosToMap(BlockPos pos) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", pos.getX());
        map.put("y", pos.getY());
        map.put("z", pos.getZ());
        return map;
    }

    private static String quote(String text) {
        if (text == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20 || c > 0x7e) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    public static String getExperimentDir() {
        return experimentDir != null ? experimentDir.toString() : "unknown";
    }
}
