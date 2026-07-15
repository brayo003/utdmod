package com.utdmod.core;

import com.utdmod.diag.DiagnosticLogs;
import com.utdmod.experiment.ExperimentTelemetry;
import com.utdmod.field.FieldActuatorRegistry;
import com.utdmod.interpretation.InterpretationService;
import com.utdmod.interpretation.RegionalWeatherCoordinator;
import com.utdmod.interpretation.WorldPresentationState;
import com.utdmod.network.TensionSyncPacket;
import com.utdmod.research.PerturbationType;
import com.utdmod.research.ResearchBotManager;
import com.utdmod.research.ResearchObserver;
import com.utdmod.research.ResearchPerturbationBridge;
import com.utdmod.research.player.ResearchPlayerRegistry;
import com.utdmod.signals.PlayerStateIntegrator;
import com.utdmod.storm.StormManager;
import com.utdmod.tension.ChunkTensionData;
import com.utdmod.tension.ChunkTensionManager;
import com.utdmod.tension.SubstrateObservationModel;
import com.utdmod.tension.SubstrateState;
import com.utdmod.tick.TensionLogger;
import com.utdmod.UTDMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server tick pipeline:
 *   player activity → substrate impulse → substrate integration → metric computation → sync
 *
 * The legacy local/global tension ODEs have been removed.
 * Substrate state (activation, memory, field depth, baseline) is the only dynamical field.
 * Local tension, regional tension, and global tension are all derived metrics.
 */
public final class TensionServerTick {

    private static long tickCounter = 0;

    private static final Map<UUID, double[]> lastPlayerPos = new HashMap<>();
    private static final Map<String, Double> cumulativeSources = new HashMap<>();
    private static final Map<UUID, Integer> lastKnownRegionByPlayer = new LinkedHashMap<>();
    private static int lastGlobalLevel = 0;

    public static long getTickCounter() { return tickCounter; }

    private static void addCumulativeSource(String source, double amount) {
        cumulativeSources.merge(source, amount, Double::sum);
    }

    private static void logCumulativeSources() {
        System.out.println("===== TENSION SOURCE TOTALS =====");
        for (var e : cumulativeSources.entrySet()) {
            System.out.printf("%s -> %.4f%n", e.getKey(), e.getValue());
        }
    }

    private TensionServerTick() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TensionServerTick::onEndServerTick);
    }

    public static void triggerLocalStorm(ChunkPos chunkPos, ServerWorld world) {
        if (UTDMod.LOGGER.isDebugEnabled()) {
            UTDMod.LOGGER.debug("Local storm triggered in chunk {},{} by substrate field state",
                chunkPos.x, chunkPos.z);
        }
        TensionTraceLogger.traceField(
            world.getTime(), "SYSTEM", "LOCAL_STORM_START", "local_storm_activated",
            chunkPos,
            ChunkTensionManager.getLocalTension(world, chunkPos),
            TensionManager.getTension(),
            ChunkTensionManager.getChunkState(world, chunkPos).name(),
            "local_storm_activated"
        );
    }

    private static void onEndServerTick(MinecraftServer server) {
        if (tickCounter == 0) {
            com.utdmod.experiment.ExperimentLogger.ensureInitialized(server);
        }
        tickCounter++;
        TensionTraceClock.setServerTick(tickCounter);
        TensionActivityLedger.reset();

        // 1. Player activity → substrate impulses
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            trackPlayerMovement(player);
        }
        for (ServerPlayerEntity player : ResearchPlayerRegistry.getAll()) {
            if (server.getPlayerManager().getPlayerList().contains(player)) continue;
            trackPlayerMovement(player);
        }

        // 2. Substrate integration (every 5 ticks)
        if (tickCounter % 5 == 0) {
            tickSubstrate(server);
        }

        // 3. Region refresh (every 20 ticks)
        if (tickCounter % 20 == 0) {
            RegionManager.refresh(server, tickCounter);
        }

        // 4. Global metric recompute from substrate (every tick, cheap)
        TensionManager.recompute(server);

        // 5. Region diagnostics (every 200 ticks)
        if (tickCounter % 200 == 0) {
            RegionDiagnosticsManager.refresh(server, tickCounter);
        }

        // 6. Player region events (every 20 ticks)
        if (tickCounter % 20 == 0) {
            updatePlayerRegionEvents(server);
        }

        // 7. HUD + diagnostics (every 40 ticks)
        if (tickCounter % 40 == 0) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                ServerWorld world = player.getServerWorld();
                ChunkPos chunkPos = new ChunkPos(player.getBlockPos());
                SubstrateState ss = ChunkTensionManager.getOrCreateSubstrateState(world, chunkPos);
                double localT = SubstrateObservationModel.computeLocalTension(ss);
                ChunkTensionData.ChunkState state = ChunkTensionManager.getChunkState(world, chunkPos);
                double globalT = TensionManager.getTension();
                String corruptionLevel = TensionManager.getCorruptionLevelName();
                String status = TensionManager.isStormActive() ? "STORM" : "Calm";
                player.sendMessage(Text.literal(String.format(
                    "Tension: %.2f (%s) | Global: %.2f (%s) | %s",
                    localT, state, globalT, corruptionLevel, status)), true);
            }
            int newGlobalLevel = (int) Math.min(3, Math.floor(TensionManager.getTension() * 4));
            if (newGlobalLevel != lastGlobalLevel) {
                DiagnosticLogs.globalLevelChanged(lastGlobalLevel, newGlobalLevel);
                lastGlobalLevel = newGlobalLevel;
            }
        }

        // 8. Field summary diagnostics (every 20 ticks)
        if (tickCounter % 20 == 0) {
            var regions = RegionManager.getRegions();
            int regionCount = regions.size();
            int largestSize = regions.stream().mapToInt(Region::getChunkCount).max().orElse(0);
            int totalStorm = regions.stream().mapToInt(Region::getStormChunks).sum();
            int totalFractured = regions.stream().mapToInt(Region::getFracturedChunks).sum();
            double avgMaturity = regions.stream().mapToDouble(Region::getMaturity).average().orElse(0.0);
            double totalContrib = regions.stream().mapToDouble(Region::getContribution).sum();
            DiagnosticLogs.fieldSummary(tickCounter, TensionManager.getTension(),
                regionCount, largestSize, totalStorm, totalFractured, avgMaturity, totalContrib);
        }

        if (tickCounter % 100 == 0) {
            DiagnosticLogs.regionSummary(tickCounter, RegionManager.getRegions());
        }

        // 9. Sync to clients (every 20 ticks)
        if (tickCounter % 20 == 0) {
            TensionSyncPacket.sendToAllPlayers(server, TensionManager.getTension(), TensionManager.isStormActive());
        }

        // 10. Storm tick
        for (ServerWorld sw : server.getWorlds()) {
            StormManager.tick(sw);
        }

        // 11. Research / experiment infrastructure
        ResearchBotManager.tick(server, tickCounter);
        ResearchObserver.tick(server, tickCounter);
        ExperimentTelemetry.tick(server, tickCounter);
        com.utdmod.experiment.ExperimentLogger.maybeLogFieldSummary(server, tickCounter);

        if (tickCounter % (20 * 20) == 0) {
            logCumulativeSources();
        }
    }

    /**
     * Substrate integration tick.
     * Collects loaded chunks that have substrate state, integrates the PDE,
     * then runs interpretation and actuators.
     * No legacy tension ODE is applied here.
     */
    private static void tickSubstrate(MinecraftServer server) {
        for (ServerWorld sw : server.getWorlds()) {
            ChunkTensionData chunkData = ChunkTensionData.getServerState(sw);

            List<ChunkPos> loadedSubstrateChunks = new ArrayList<>();
            for (ChunkPos pos : chunkData.getSubstrateStatesView().keySet()) {
                if (sw.isChunkLoaded(pos.x, pos.z)) {
                    loadedSubstrateChunks.add(pos);
                }
            }

            // Substrate PDE integration (gather/apply double-buffer)
            chunkData.integrateAllSubstrateStates(sw, loadedSubstrateChunks);

            // Interpretation and actuators
            var interpretations = InterpretationService.interpretWorld(sw, loadedSubstrateChunks);
            RegionalWeatherCoordinator.applyWorldWeather(sw, interpretations);
            WorldPresentationState.update(sw, interpretations);

            for (ChunkPos pos : loadedSubstrateChunks) {
                var interpretation = interpretations.get(pos);
                if (interpretation != null) {
                    FieldActuatorRegistry.actuateAll(sw, interpretation);
                }

                // Chunk state transitions driven by derived tension metric
                SubstrateState ss = chunkData.getSubstrateState(pos);
                if (ss != null) {
                    double t = SubstrateObservationModel.computeLocalTension(ss);
                    updateChunkStateFromMetric(chunkData, pos, t, sw);
                }

                chunkData.tickContaminationMemory(pos, chunkData.getLocalTension(pos), sw.getTime());
            }

            // Prune inactive substrate entries (every ~5 min)
            if (tickCounter % (20 * 60 * 5) == 0) {
                chunkData.pruneInactive(45L * 60L * 1000L);
            }
        }
    }

    /**
     * Drive ChunkState transitions from the derived tension metric T(x).
     * Replaces the legacy ODE-based state transitions.
     */
    private static void updateChunkStateFromMetric(ChunkTensionData chunkData, ChunkPos pos,
                                                    double t, ServerWorld world) {
        // Storm flag on chunk
        boolean stormHere = chunkData.isStormActive(pos);
        if (t > 0.95 && !stormHere) {
            chunkData.setStormActive(pos, true);
            triggerLocalStorm(pos, world);
        }
        if (t < 0.25) {
            chunkData.setStormActive(pos, false);
        }
        chunkData.updateChunkClassification(pos, t, world);
    }

    private static void updatePlayerRegionEvents(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            Region region = RegionManager.getRegionForChunk(
                player.getServerWorld(), new ChunkPos(player.getBlockPos()));
            if (region == null) continue;
            Integer previousRegionId = lastKnownRegionByPlayer.get(player.getUuid());
            if (previousRegionId == null) {
                DiagnosticLogs.playerEnterRegion(player.getName().getString(), region.getId(), region.getState().name());
            } else if (!previousRegionId.equals(region.getId())) {
                DiagnosticLogs.playerExitRegion(player.getName().getString(), previousRegionId);
                DiagnosticLogs.playerEnterRegion(player.getName().getString(), region.getId(), region.getState().name());
            }
            lastKnownRegionByPlayer.put(player.getUuid(), region.getId());
        }
        lastKnownRegionByPlayer.keySet().removeIf(id -> server.getPlayerManager().getPlayer(id) == null);
    }

    private static void trackPlayerMovement(ServerPlayerEntity player) {
        PlayerStateIntegrator.onServerPlayerTick(player);

        ServerWorld world = player.getServerWorld();
        double[] last = lastPlayerPos.computeIfAbsent(player.getUuid(), ignored -> new double[3]);
        double cx = player.getX(), cy = player.getY(), cz = player.getZ();

        if (player.isSpectator() || player.isCreative()) {
            last[0] = cx; last[1] = cy; last[2] = cz;
            return;
        }

        double dx = cx - last[0], dy = cy - last[1], dz = cz - last[2];
        if (dx * dx + dy * dy + dz * dz > 0.01) {
            ChunkPos chunkPos = new ChunkPos(player.getBlockPos());
            String moveAction = "walk";
            double amount = 0.0015;
            if (player.isFallFlying())                              { moveAction = "elytra"; amount = 0.0035; }
            else if (player.isSprinting())                          { moveAction = "sprint"; amount = 0.0022; }
            else if (player.isTouchingWater() || player.isSubmergedInWater()) { moveAction = "swim"; amount = 0.002; }
            if (world.getRegistryKey().equals(World.NETHER))        { moveAction += "_nether"; amount *= 1.25; }

            boolean applied = ResearchPerturbationBridge.applyLocalPerturbation(
                world, chunkPos, player.getBlockPos(), player, PerturbationType.MOVEMENT, moveAction, amount);
            if (!applied) {
                // Inject as substrate impulse rather than legacy localTension
                chunkData(world).addInstantaneousImpulse(chunkPos, amount);
                TensionLogger.log(tickCounter, "MOVEMENT", moveAction, amount, 0.0, amount, chunkPos, player, world);
            }
            addCumulativeSource("MOVEMENT", amount);
            TensionActivityLedger.addMovement(amount);
        }
        last[0] = cx; last[1] = cy; last[2] = cz;
    }

    private static ChunkTensionData chunkData(ServerWorld world) {
        return ChunkTensionData.getServerState(world);
    }
}
