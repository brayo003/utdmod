package com.utdmod.core;

import com.utdmod.config.UTDConfig;
import com.utdmod.tension.ChunkTensionData;
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
 * Builds and exposes live region aggregates from active chunk tension entries.
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

    /**
     * Return the active Region containing the given chunk in the given world, or null.
     * This is intentionally O(n) over active regions because it's only called on the
     * server side infrequently (per-player sync, every 20 ticks).
     */
    public static Region getRegionForChunk(net.minecraft.server.world.ServerWorld world, net.minecraft.util.math.ChunkPos chunkPos) {
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
            Map<ChunkPos, Double> tensionMap = chunkData.getTensionMap();
            Map<ChunkPos, Double> activeChunks = new HashMap<>();

            for (Map.Entry<ChunkPos, Double> entry : tensionMap.entrySet()) {
                ChunkPos chunkPos = entry.getKey();
                if (!world.isChunkLoaded(chunkPos.x, chunkPos.z)) {
                    continue;
                }
                double tension = entry.getValue();
                if (tension > UTDConfig.regionActiveTensionThreshold) {
                    activeChunks.put(chunkPos, tension);
                }
            }

            if (activeChunks.isEmpty()) {
                continue;
            }

            Set<ChunkPos> visited = new HashSet<>();
            Deque<ChunkPos> queue = new ArrayDeque<>();

            for (ChunkPos seed : activeChunks.keySet()) {
                if (!visited.add(seed)) {
                    continue;
                }

                queue.clear();
                queue.add(seed);

                Set<ChunkPos> regionChunks = new HashSet<>();
                double tensionSum = 0.0;
                double tensionMax = 0.0;
                int fracturedCount = 0;
                int decoupledCount = 0;
                int stormCount = 0;
                double centroidSumX = 0.0;
                double centroidSumZ = 0.0;

                while (!queue.isEmpty()) {
                    ChunkPos current = queue.removeFirst();
                    regionChunks.add(current);

                    double tension = activeChunks.get(current);
                    tensionSum += tension;
                    tensionMax = Math.max(tensionMax, tension);
                    centroidSumX += current.x;
                    centroidSumZ += current.z;

                    ChunkTensionData.ChunkState state = chunkData.getChunkState(current);
                    if (state == ChunkTensionData.ChunkState.FRACTURED) {
                        fracturedCount++;
                    } else if (state == ChunkTensionData.ChunkState.DECOUPLED) {
                        decoupledCount++;
                    }
                    if (chunkData.isStormActive(current)) {
                        stormCount++;
                    }

                    for (int i = 0; i < DX.length; i++) {
                        ChunkPos neighbor = new ChunkPos(current.x + DX[i], current.z + DZ[i]);
                        if (activeChunks.containsKey(neighbor) && visited.add(neighbor)) {
                            queue.add(neighbor);
                        }
                    }
                }

                int chunkCount = regionChunks.size();
                double average = chunkCount > 0 ? tensionSum / chunkCount : 0.0;
                double centroidX = chunkCount > 0 ? centroidSumX / chunkCount : 0.0;
                double centroidZ = chunkCount > 0 ? centroidSumZ / chunkCount : 0.0;

                Region previousRegion = null;
                int bestOverlap = 0;
                Map<Region, Integer> overlapCounts = new HashMap<>();
                for (ChunkPos chunk : regionChunks) {
                    Region candidate = previousRegionByChunk.get(new ChunkKey(world.getRegistryKey(), chunk));
                    if (candidate == null) {
                        continue;
                    }
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
                    average,
                    tensionMax,
                    fracturedCount,
                    decoupledCount,
                    stormCount,
                    centroidX,
                    centroidZ,
                    age,
                    tick,
                    maturity
                );
                region.updateMaturity(tick);
                nextRegions.add(region);

                for (ChunkPos chunk : regionChunks) {
                    nextRegionByChunk.put(new ChunkKey(world.getRegistryKey(), chunk), region);
                }
            }
        }

        List<Region> previousRegions = new ArrayList<>(regions);
        Map<Integer, Region> previousRegionsByIdSnapshot = new HashMap<>(previousRegionsById);
        previousRegionsById.clear();
        for (Region region : nextRegions) {
            previousRegionsById.put(region.getId(), region);
        }

        for (Region previousRegion : previousRegions) {
            boolean stillPresent = nextRegions.stream().anyMatch(region -> region.getId() == previousRegion.getId());
            if (stillPresent) {
                continue;
            }

            Region mergedInto = nextRegions.stream()
                .filter(region -> region.getChunks().containsAll(previousRegion.getChunks()))
                .findFirst()
                .orElse(null);
            if (mergedInto != null) {
                com.utdmod.diag.DiagnosticLogs.regionMerged(previousRegion.getId(), mergedInto.getId());
            } else {
                com.utdmod.diag.DiagnosticLogs.regionDestroyed(previousRegion.getId(), previousRegion.getChunkCount());
            }
        }

        for (Region region : nextRegions) {
            Region previousRegion = previousRegionsByIdSnapshot.get(region.getId());
            if (previousRegion == null) {
                com.utdmod.diag.DiagnosticLogs.regionCreated(region.getId(), region.getChunkCount(), region.getState().name());
            } else if (!previousRegion.getState().equals(region.getState())) {
                com.utdmod.diag.DiagnosticLogs.regionStateChanged(region.getId(), previousRegion.getState().name(), region.getState().name());
            }
        }

        regions = Collections.unmodifiableList(nextRegions);
        previousRegionByChunk.clear();
        previousRegionByChunk.putAll(nextRegionByChunk);
    }

    private static void logDiagnostics(List<Region> regionList) {
        System.out.println("[REGIONS]");
        System.out.printf("count=%d%n", regionList.size());

        int largestId = 0;
        int largestSize = 0;
        double largestAvg = 0.0;
        double largestMaturity = 0.0;

        for (Region region : regionList) {
            if (region.getChunkCount() > largestSize) {
                largestSize = region.getChunkCount();
                largestId = region.getId();
                largestAvg = region.getAverageTension();
                largestMaturity = region.getMaturity();
            }
        }

        System.out.printf("largest=%d%n", largestId);
        System.out.printf("largestSize=%d%n", largestSize);
        System.out.printf("largestAvg=%.4f%n", largestAvg);
        System.out.printf("largestMaturity=%.4f%n", largestMaturity);

        for (Region region : regionList) {
            System.out.printf("id=%d size=%d avg=%.4f maturity=%.4f age=%d state=%s fractured=%d storm=%d%n",
                region.getId(),
                region.getChunkCount(),
                region.getAverageTension(),
                region.getMaturity(),
                region.getAge(),
                region.getState(),
                region.getFracturedChunks(),
                region.getStormChunks()
            );
        }
    }
}
