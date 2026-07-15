package com.utdmod.core;

import com.utdmod.tension.ChunkTensionData;
import com.utdmod.tension.SubstrateObservationModel;
import com.utdmod.tension.SubstrateState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Builds and exposes live region aggregates from substrate state.
 * Regions are connected components of chunks whose derived tension T(x) exceeds the active threshold.
 * All statistics are computed from substrate variables — no legacy tension map is consulted.
 */
public final class RegionManager {
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1};

    private static volatile List<Region> regions = List.of();
    private static final Map<ChunkKey, Region> previousRegionByChunk = new HashMap<>();
    private static final Map<Integer, Region> previousRegionsById = new HashMap<>();

    private RegionManager() {}

    private static final class ChunkKey {
        private final RegistryKey<World> worldKey;
        private final ChunkPos chunkPos;

        private ChunkKey(RegistryKey<World> worldKey, ChunkPos chunkPos) {
            this.worldKey = worldKey;
            this.chunkPos = chunkPos;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChunkKey)) return false;
            ChunkKey other = (ChunkKey) o;
            return worldKey.equals(other.worldKey) && chunkPos.equals(other.chunkPos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(worldKey, chunkPos);
        }
    }

    public static List<Region> getRegions() {
        return regions;
    }

    public static Region getRegionForChunk(ServerWorld world, ChunkPos chunkPos) {
        if (world == null) return null;
        var rlist = regions;
        for (Region r : rlist) {
            if (r.getWorldKey().equals(world.getRegistryKey()) && r.getChunks().contains(chunkPos)) {
                return r;
            }
        }
        return null;
    }

    public static void refresh(MinecraftServer server, long tick) {
        List<Region> nextRegions = new ArrayList<>();
        Map<ChunkKey, Region> nextRegionByChunk = new HashMap<>();
        int nextId = 1;

        for (ServerWorld world : server.getWorlds()) {
            ChunkTensionData chunkData = ChunkTensionData.getServerState(world);
            Map<ChunkPos, SubstrateState> substrateView = chunkData.getSubstrateStatesView();

            // Build active-chunk map: T(x) > threshold, chunk loaded
            Map<ChunkPos, Double> activeChunks = new HashMap<>();
            for (Map.Entry<ChunkPos, SubstrateState> entry : substrateView.entrySet()) {
                ChunkPos pos = entry.getKey();
                if (!world.isChunkLoaded(pos.x, pos.z)) continue;
                double t = SubstrateObservationModel.computeLocalTension(entry.getValue());
                if (t > SubstrateState.ACTIVE_TENSION_THRESHOLD) {
                    activeChunks.put(pos, t);
                }
            }

            if (activeChunks.isEmpty()) continue;

            Set<ChunkPos> visited = new HashSet<>();
            Deque<ChunkPos> queue = new ArrayDeque<>();

            for (ChunkPos seed : activeChunks.keySet()) {
                if (!visited.add(seed)) continue;

                queue.clear();
                queue.add(seed);

                Set<ChunkPos> regionChunks = new HashSet<>();
                double tensionSum = 0.0;
                double tensionSumSq = 0.0;
                double tensionMax = 0.0;
                int activeCount = 0;
                double depthSum = 0.0;
                double memorySum = 0.0;
                double activationSum = 0.0;
                double activationMax = 0.0;
                int fracturedCount = 0;
                int decoupledCount = 0;
                int stormCount = 0;
                double centroidSumX = 0.0;
                double centroidSumZ = 0.0;

                while (!queue.isEmpty()) {
                    ChunkPos current = queue.removeFirst();
                    regionChunks.add(current);

                    double t = activeChunks.get(current);
                    tensionSum += t;
                    tensionSumSq += t * t;
                    if (t > tensionMax) tensionMax = t;
                    if (t > SubstrateState.ACTIVE_TENSION_THRESHOLD) activeCount++;
                    centroidSumX += current.x;
                    centroidSumZ += current.z;

                    SubstrateState ss = substrateView.get(current);
                    if (ss != null) {
                        depthSum += ss.getFieldDepth();
                        memorySum += ss.getMemory();
                        double a = ss.getActivation();
                        activationSum += a;
                        if (a > activationMax) activationMax = a;
                    }

                    ChunkTensionData.ChunkState state = chunkData.getChunkState(current);
                    if (state == ChunkTensionData.ChunkState.FRACTURED) fracturedCount++;
                    else if (state == ChunkTensionData.ChunkState.DECOUPLED) decoupledCount++;
                    if (chunkData.isStormActive(current)) stormCount++;

                    for (int i = 0; i < DX.length; i++) {
                        ChunkPos neighbor = new ChunkPos(current.x + DX[i], current.z + DZ[i]);
                        if (activeChunks.containsKey(neighbor) && visited.add(neighbor)) {
                            queue.add(neighbor);
                        }
                    }
                }

                int n = regionChunks.size();
                double mean = n > 0 ? tensionSum / n : 0.0;
                double variance = n > 1 ? (tensionSumSq / n) - (mean * mean) : 0.0;
                double activeFraction = n > 0 ? (double) activeCount / n : 0.0;
                double meanDepth = n > 0 ? depthSum / n : 0.0;
                double meanMemory = n > 0 ? memorySum / n : 0.0;
                double meanActivation = n > 0 ? activationSum / n : 0.0;
                double centroidX = n > 0 ? centroidSumX / n : 0.0;
                double centroidZ = n > 0 ? centroidSumZ / n : 0.0;

                // Inherit age and maturity from best-overlap predecessor
                Region previousRegion = null;
                int bestOverlap = 0;
                Map<Region, Integer> overlapCounts = new HashMap<>();
                for (ChunkPos chunk : regionChunks) {
                    Region candidate = previousRegionByChunk.get(new ChunkKey(world.getRegistryKey(), chunk));
                    if (candidate == null) continue;
                    int overlap = overlapCounts.getOrDefault(candidate, 0) + 1;
                    overlapCounts.put(candidate, overlap);
                    if (overlap > bestOverlap) {
                        bestOverlap = overlap;
                        previousRegion = candidate;
                    }
                }

                int age = 1;
                double maturity = 0.0;
                if (previousRegion != null) {
                    age = previousRegion.getAge() + 1;
                    maturity = previousRegion.getMaturity();
                }

                Region region = new Region(
                    nextId++,
                    world.getRegistryKey(),
                    regionChunks,
                    mean, tensionMax, variance, activeFraction,
                    meanDepth, meanMemory, meanActivation, activationMax,
                    fracturedCount, decoupledCount, stormCount,
                    centroidX, centroidZ,
                    age, tick, maturity
                );
                region.updateMaturity(tick);
                nextRegions.add(region);

                for (ChunkPos chunk : regionChunks) {
                    nextRegionByChunk.put(new ChunkKey(world.getRegistryKey(), chunk), region);
                }
            }
        }

        // Lifecycle events
        List<Region> previousRegions = new ArrayList<>(regions);
        Map<Integer, Region> previousRegionsByIdSnapshot = new HashMap<>(previousRegionsById);
        previousRegionsById.clear();
        for (Region region : nextRegions) {
            previousRegionsById.put(region.getId(), region);
        }

        for (Region prev : previousRegions) {
            boolean stillPresent = nextRegions.stream().anyMatch(r -> r.getId() == prev.getId());
            if (stillPresent) continue;
            Region mergedInto = nextRegions.stream()
                .filter(r -> r.getChunks().containsAll(prev.getChunks()))
                .findFirst().orElse(null);
            if (mergedInto != null) {
                com.utdmod.diag.DiagnosticLogs.regionMerged(prev.getId(), mergedInto.getId());
            } else {
                com.utdmod.diag.DiagnosticLogs.regionDestroyed(prev.getId(), prev.getChunkCount());
            }
        }

        for (Region region : nextRegions) {
            Region prev = previousRegionsByIdSnapshot.get(region.getId());
            if (prev == null) {
                com.utdmod.diag.DiagnosticLogs.regionCreated(region.getId(), region.getChunkCount(), region.getState().name());
            } else if (!prev.getState().equals(region.getState())) {
                com.utdmod.diag.DiagnosticLogs.regionStateChanged(region.getId(), prev.getState().name(), region.getState().name());
            }
        }

        regions = Collections.unmodifiableList(nextRegions);
        previousRegionByChunk.clear();
        previousRegionByChunk.putAll(nextRegionByChunk);
    }
}
