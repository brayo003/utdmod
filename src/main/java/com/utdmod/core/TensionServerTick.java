package com.utdmod.core;

import com.utdmod.diag.DiagnosticLogs;
import com.utdmod.experiment.ExperimentTelemetry;
import com.utdmod.network.TensionSyncPacket;
import com.utdmod.registry.ModItems;
import com.utdmod.signals.PlayerStateIntegrator;
import com.utdmod.storm.StormManager;
import com.utdmod.tension.ChunkTensionData;
import com.utdmod.tension.ChunkTensionManager;
import com.utdmod.tick.TensionLogger;
import com.utdmod.UTDMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server tick pipeline: player activity → local chunk tension → diffusion / states → global coupling → sync.
 */
public final class TensionServerTick {

    private static long tickCounter = 0;
    private static int stormCooldown = 0;
    private static double secondaryLastTension = Double.NaN;

    private static final Map<UUID, double[]> lastPlayerPos = new HashMap<>();
    private static final Map<String, Double> cumulativeSources = new HashMap<>();
    private static final Map<UUID, Integer> lastKnownRegionByPlayer = new LinkedHashMap<>();
    private static int lastGlobalLevel = 0;

    public static long getTickCounter() {
        return tickCounter;
    }

    private static void logCumulativeSources() {
        System.out.println("===== TENSION SOURCE TOTALS =====");
        for (var e : cumulativeSources.entrySet()) {
            System.out.printf("%s -> %.4f%n", e.getKey(), e.getValue());
        }
    }

    private static void addCumulativeSource(String source, double amount) {
        cumulativeSources.merge(source, amount, Double::sum);
    }

    private static final double D = 0.012;
    /** Lower decay so damaged regions linger (was 0.004). */
    private static final double LAMBDA = 0.0006;
    private static final double ALPHA = 0.009;
    /** Softer cubic saturation so tension can climb before self-limiting (was 0.02). */
    private static final double BETA = 0.055;
    /** Local uplift from global must stay subordinate to chunk mining/diffusion (calm world baseline). */
    private static final double GAMMA = 0.045;
    /** Chunk-local lightning/front: must stay above idle plateau (~0.40–0.45); needs player escalation. */
    private static final double STORM_THRESHOLD = 0.95;
    private static final double STORM_END_THRESHOLD = 0.25;
    private static final double STORM_DRAIN_RATE = 0.002;

    private TensionServerTick() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TensionServerTick::onEndServerTick);
    }

    public static void triggerLocalStorm(ChunkPos chunkPos, ServerWorld world) {
        for (int i = 0; i < 2 + world.random.nextInt(2); i++) {
            double x = (chunkPos.x << 4) + world.random.nextDouble() * 16;
            double z = (chunkPos.z << 4) + world.random.nextDouble() * 16;

            int y = world.getTopY();
            BlockPos pos = new BlockPos((int) x, y, (int) z);
            while (y > 0 && world.getBlockState(pos).isAir()) {
                y--;
                pos = pos.down();
            }
            y++;

            LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world);
            if (lightning != null) {
                lightning.refreshPositionAfterTeleport(new Vec3d(x, y, z));
                world.spawnEntity(lightning);
                if (UTDMod.LOGGER.isDebugEnabled()) {
                    UTDMod.LOGGER.debug("Lightning spawned at {}, {}, {} in chunk {},{}", x, y, z, chunkPos.x, chunkPos.z);
                }
            }
        }
        TensionTraceLogger.traceField(
            world.getTime(),
            "SYSTEM",
            "LOCAL_STORM_START",
            "lightning_burst",
            chunkPos,
            ChunkTensionManager.getLocalTension(world, chunkPos),
            TensionManager.getTension(),
            ChunkTensionManager.getChunkState(world, chunkPos).name(),
            "local_storm_activated"
        );
    }

    /**
     * Drives global tension from chunk statistics (mean, spread, storms, high-corruption chunks).
     */
    private static CouplingSplit computeChunkCouplingSplit(MinecraftServer server) {
        var regions = RegionManager.getRegions();
        int regionCount = regions.size();
        double totalContribution = 0.0;
        int totalStormChunks = 0;
        int totalFracturedChunks = 0;
        int largestRegionSize = 0;
        double largestContribution = 0.0;

        for (Region region : regions) {
            double contribution = region.getContribution();
            totalContribution += contribution;
            totalStormChunks += region.getStormChunks();
            totalFracturedChunks += region.getFracturedChunks();
            if (region.getChunkCount() > largestRegionSize) {
                largestRegionSize = region.getChunkCount();
                largestContribution = contribution;
            }
        }

        double averageContribution = regionCount > 0 ? totalContribution / regionCount : 0.0;
        double ambient = 0.0035 * totalContribution;
        double regional = 0.052 * totalContribution;

        if (tickCounter % 20 == 0) {
            double averageRegionMaturity = regionCount > 0
                ? regions.stream().mapToDouble(Region::getMaturity).average().orElse(0.0)
                : 0.0;
            DiagnosticLogs.fieldSummary(
                tickCounter,
                TensionManager.getTension(),
                regionCount,
                largestRegionSize,
                totalStormChunks,
                totalFracturedChunks,
                averageRegionMaturity,
                totalContribution
            );
        }

        if (tickCounter % 100 == 0) {
            DiagnosticLogs.regionSummary(tickCounter, regions);
        }

        return new CouplingSplit(ambient, regional, 0.0, 0.0, totalStormChunks, totalFracturedChunks);
    }

    private static void onEndServerTick(MinecraftServer server) {
        tickCounter++;
        TensionTraceClock.setServerTick(tickCounter);
        TensionActivityLedger.reset();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlayerStateIntegrator.onServerPlayerTick(player);

            ServerWorld world = player.getServerWorld();
            double[] last = lastPlayerPos.get(player.getUuid());
            if (last == null) {
                last = new double[3];
                lastPlayerPos.put(player.getUuid(), last);
            }
            double cx = player.getX();
            double cy = player.getY();
            double cz = player.getZ();

            if (player.isSpectator() || player.isCreative()) {
                last[0] = cx;
                last[1] = cy;
                last[2] = cz;
                continue;
            }

            double dx = cx - last[0];
            double dy = cy - last[1];
            double dz = cz - last[2];
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > 0.01) {
                ChunkPos chunkPos = new ChunkPos(player.getBlockPos());
                String moveAction = "walk";
                double amount = 0.0015;
                if (player.isFallFlying()) {
                    moveAction = "elytra";
                    amount = 0.0035;
                } else if (player.isSprinting()) {
                    moveAction = "sprint";
                    amount = 0.0022;
                } else if (player.isTouchingWater() || player.isSubmergedInWater()) {
                    moveAction = "swim";
                    amount = 0.002;
                }
                if (world.getRegistryKey().equals(World.NETHER)) {
                    moveAction = moveAction + "_nether";
                    amount *= 1.25;
                }
                double before = ChunkTensionManager.getLocalTension(world, chunkPos);
                ChunkTensionManager.addLocalTension(world, chunkPos, amount);
                double after = ChunkTensionManager.getLocalTension(world, chunkPos);
                addCumulativeSource("MOVEMENT", amount);
                TensionActivityLedger.addMovement(amount);
                TensionLogger.log(tickCounter, "MOVEMENT", moveAction, amount, before, after, chunkPos, player, world);
            }
            last[0] = cx;
            last[1] = cy;
            last[2] = cz;
        }

        if (tickCounter % 5 == 0) {
            tickChunkTension(server);
        }

        if (tickCounter % 20 == 0) {
            RegionManager.refresh(server, tickCounter);
        }

        CouplingSplit split = computeChunkCouplingSplit(server);

        if (tickCounter % 20 == 0 && DiagnosticLogs.isGlobalFlowDebugEnabled()) {
            System.out.printf(
                "[COUPLING] tick=%d storms=%d fractured=%d ambient=%.6f regional=%.6f total=%.6f%n",
                tickCounter, split.stormChunks, split.fracturedChunks,
                split.ambientInflow, split.localCoupling, split.total);
        }

        // Log-only: nonzero when chunk storms, global/regional storm hysteresis, or fractured mass (not a constant floor).
        boolean stormDriveContext = split.stormChunks > 0
            || split.fracturedChunks > 0
            || StormManager.isStormActive()
            || TensionManager.isStormActive();
        double stormDiag = stormDriveContext ? 0.026 * split.stormChunkFrac + 0.042 * split.fracturedFrac : 0.0;
        TensionManager.tickGlobalDynamics(split.total, stormDiag, tickCounter, split.ambientInflow, split.localCoupling);

        if (tickCounter % 200 == 0) {
            RegionDiagnosticsManager.refresh(server, tickCounter);
        }

        if (tickCounter % 20 == 0) {
            updatePlayerRegionEvents(server);
        }

        if (TensionManager.isStormActive()) {
            if (stormCooldown > 0) {
                stormCooldown--;
            }
            if (stormCooldown <= 0) {
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    ServerWorld world = player.getServerWorld();
                    Vec3d pos = player.getPos();
                    LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world);
                    if (lightning != null) {
                        lightning.refreshPositionAfterTeleport(pos);
                        world.spawnEntity(lightning);
                    }
                }
                stormCooldown = 200;
            }
            if (tickCounter % 40 == 0) {
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    player.getHungerManager().addExhaustion(0.5f);
                }
            }
        } else {
            stormCooldown = 0;
        }

        if (tickCounter % (20 * 20) == 0) {
            logCumulativeSources();
        }

        if (tickCounter % 20 == 0) {
            runWeatherAndRitualSecondary(server);
        }

        if (tickCounter % 40 == 0) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                ServerWorld world = player.getServerWorld();
                ChunkPos chunkPos = new ChunkPos(player.getBlockPos());
                double localTension = ChunkTensionManager.getLocalTension(world, chunkPos);
                ChunkTensionData.ChunkState state = ChunkTensionManager.getChunkState(world, chunkPos);
                double globalTension = TensionManager.getTension();
                String corruptionLevel = TensionManager.getCorruptionLevelName();
                String status = TensionManager.isStormActive() ? "STORM" : "Calm";
                player.sendMessage(Text.literal(String.format(
                    "Chunk Tension: %.2f (%s) | Global: %.2f (%s) | %s",
                    localTension, state, globalTension, corruptionLevel, status)), true);
            }
            int newGlobalLevel = (int) Math.min(3, Math.floor(TensionManager.getTension()));
            if (newGlobalLevel != lastGlobalLevel) {
                DiagnosticLogs.globalLevelChanged(lastGlobalLevel, newGlobalLevel);
                lastGlobalLevel = newGlobalLevel;
            }
        }

        if (tickCounter % 20 == 0) {
            TensionSyncPacket.sendToAllPlayers(server, TensionManager.getTension(), TensionManager.isStormActive());
        }

        for (ServerWorld sw : server.getWorlds()) {
            StormManager.tick(sw);
        }

        ExperimentTelemetry.tick(server, tickCounter);
    }

    private static void tickChunkTension(MinecraftServer server) {
        for (ServerWorld sw : server.getWorlds()) {
            ChunkTensionData chunkData = ChunkTensionData.getServerState(sw);
            double globalTension = TensionManager.getTension();

            var tensionChunks = new HashMap<>(chunkData.getTensionMap());

            for (var entry : tensionChunks.entrySet()) {
                ChunkPos chunkPos = entry.getKey();
                if (!sw.isChunkLoaded(chunkPos.x, chunkPos.z)) {
                    continue;
                }

                double localTension = entry.getValue();
                boolean stormHere = chunkData.isStormActive(chunkPos);
                // Sparse updates: very calm chunks share work across ticks (only loaded entries are iterated).
                if (localTension < 0.22 && !stormHere) {
                    int stride = 8;
                    int slot = Math.floorMod(chunkPos.x + chunkPos.z * 31, stride);
                    if (Math.floorMod(tickCounter / 5, stride) != slot) {
                        continue;
                    }
                }

                ChunkTensionData.ChunkState state = chunkData.getChunkState(chunkPos);

                double neighborAvg = 0.0;
                int neighborCount = 0;
                ChunkPos[] neighbors = {
                    new ChunkPos(chunkPos.x + 1, chunkPos.z),
                    new ChunkPos(chunkPos.x - 1, chunkPos.z),
                    new ChunkPos(chunkPos.x, chunkPos.z + 1),
                    new ChunkPos(chunkPos.x, chunkPos.z - 1)
                };
                for (ChunkPos n : neighbors) {
                    double nTension = chunkData.getLocalTension(n);
                    if (nTension > 0) {
                        neighborAvg += nTension;
                        neighborCount++;
                    }
                }
                if (neighborCount > 0) {
                    neighborAvg /= neighborCount;
                }

                boolean stormActive = stormHere;
                double effectiveAlpha = stormActive ? ALPHA * 1.8 : ALPHA;

                double diffusion = D * neighborAvg;
                double decayMul = chunkData.getContaminationDecayMultiplier(chunkPos);
                double decay = LAMBDA * localTension * decayMul;
                double nonlinear = effectiveAlpha * localTension * localTension;
                double saturation = BETA * localTension * localTension * localTension;
                double globalFeedback = GAMMA * globalTension;

                localTension += diffusion + nonlinear - saturation + globalFeedback;
                if (state != ChunkTensionData.ChunkState.DECOUPLED) {
                    localTension -= decay;
                }
                if (stormActive) {
                    localTension -= STORM_DRAIN_RATE;
                }

                localTension = Math.max(0, Math.min(localTension, 5.0));

                if (DiagnosticLogs.isPerChunkDebugEnabled()) {
                    System.out.printf(
                        "[UTD-CHUNK] chunk=%d,%d local=%.4f diff=%.6f nonlin=%.6f sat=%.6f decay=%.6f feedback=%.6f%n",
                        chunkPos.x,
                        chunkPos.z,
                        localTension,
                        diffusion,
                        nonlinear,
                        saturation,
                        decay,
                        globalFeedback
                    );
                }

                if (localTension > STORM_THRESHOLD && !stormActive) {
                    chunkData.setStormActive(chunkPos, true);
                    triggerLocalStorm(chunkPos, sw);
                }
                if (localTension < STORM_END_THRESHOLD) {
                    chunkData.setStormActive(chunkPos, false);
                }

                chunkData.setLocalTension(chunkPos, localTension, sw);

                chunkData.tickContaminationMemory(chunkPos, localTension, sw.getTime());
                if (chunkData.getContaminationLevel(chunkPos) > 0
                    && tickCounter % 600 == Math.floorMod(chunkPos.x + chunkPos.z * 17, 600)) {
                    double peak = chunkData.getContaminationPeak(chunkPos);
                    if (peak > 1.05) {
                        DiagnosticLogs.recovery(chunkPos, peak, localTension, LAMBDA * decayMul, sw.getTime());
                        if (tickCounter % 2400 == Math.floorMod(chunkPos.x * 31 + chunkPos.z, 2400) && localTension < 0.52) {
                            DiagnosticLogs.test3(
                                chunkPos,
                                peak,
                                (long) (Math.log(2.0) / Math.max(1e-6, LAMBDA * decayMul)),
                                -1L
                            );
                        }
                    }
                }

                double currentMiningRate = chunkData.getRecentMiningRate(chunkPos);
                if (currentMiningRate > 0) {
                    double miningDecay = currentMiningRate * 0.01;
                    chunkData.setRecentMiningRate(chunkPos, Math.max(0, currentMiningRate - miningDecay));
                }
            }

            if (tickCounter % (20 * 60 * 5) == 0) {
                chunkData.pruneInactive(45L * 60L * 1000L);
            }
        }
    }

    private static void updatePlayerRegionEvents(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            Region region = RegionManager.getRegionForChunk(player.getServerWorld(), new ChunkPos(player.getBlockPos()));
            if (region == null) {
                continue;
            }
            Integer previousRegionId = lastKnownRegionByPlayer.get(player.getUuid());
            if (previousRegionId == null) {
                DiagnosticLogs.playerEnterRegion(player.getName().getString(), region.getId(), region.getState().name());
            } else if (!previousRegionId.equals(region.getId())) {
                DiagnosticLogs.playerExitRegion(player.getName().getString(), previousRegionId);
                DiagnosticLogs.playerEnterRegion(player.getName().getString(), region.getId(), region.getState().name());
            }
            lastKnownRegionByPlayer.put(player.getUuid(), region.getId());
        }

        for (UUID playerId : lastKnownRegionByPlayer.keySet()) {
            if (server.getPlayerManager().getPlayer(playerId) == null) {
                lastKnownRegionByPlayer.remove(playerId);
            }
        }
    }

    private static void runWeatherAndRitualSecondary(MinecraftServer server) {
        double current = TensionManager.getTension();
        if (Double.isNaN(secondaryLastTension)) {
            secondaryLastTension = current;
            return;
        }
        if (Math.abs(current - secondaryLastTension) <= 0.01) {
            return;
        }

        if (current > 0.62 && secondaryLastTension <= 0.62) {
            for (ServerWorld world : server.getWorlds()) {
                if (world.isRaining()) continue;
                float p = (float) Math.min(0.55, (current - 0.62) * 2.2);
                if (world.random.nextFloat() < p) {
                    int rain = 2400 + world.random.nextInt(3600);
                    boolean thunder = current > 0.82 || world.random.nextFloat() < 0.35f;
                    world.setWeather(rain, thunder ? 1200 + world.random.nextInt(1800) : 0, true, thunder);
                }
            }
        }

        if (current > 1.0 && secondaryLastTension <= 1.0) {
            for (ServerWorld world : server.getWorlds()) {
                if (world.isRaining()) continue;
                world.setWeather(6000, 0, true, false);
            }
        } else if (current <= 1.0 && secondaryLastTension > 1.0) {
            for (ServerWorld world : server.getWorlds()) {
                if (!world.isRaining()) continue;
                world.setWeather(0, 0, false, false);
            }
        }

        if (current > 0.7) {
            for (ServerWorld world : server.getWorlds()) {
                for (ServerPlayerEntity player : world.getPlayers()) {
                    if (world.random.nextFloat() < 0.1f) {
                        player.addExperience(5);
                    }
                }
            }
        }

        for (ServerWorld world : server.getWorlds()) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (player.getMainHandStack().isOf(ModItems.WARDING_CRYSTAL) && TensionManager.getTension() > 0.5) {
                    TensionManager.setTension(Math.max(0, TensionManager.getTension() - 0.2));
                    break;
                }
            }
        }

        secondaryLastTension = current;
    }
}
