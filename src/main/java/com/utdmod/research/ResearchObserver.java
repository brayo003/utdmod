package com.utdmod.research;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.utdmod.interpretation.ChunkInterpretation;
import com.utdmod.interpretation.InterpretationService;
import com.utdmod.interpretation.InterpretationThresholds;
import com.utdmod.core.Region;
import com.utdmod.core.RegionManager;
import com.utdmod.core.TensionManager;
import com.utdmod.network.ResearchSyncPacket;
import com.utdmod.tension.ChunkTensionData;
import com.utdmod.tension.ChunkTensionManager;
import com.utdmod.tension.SubstrateState;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

public final class ResearchObserver {
    private static final List<String> narrativeLog = new ArrayList<>();
    private static long lastNarrationTick = -1;
    private static double previousMeanActivation = 0.0;
    private static double previousTransportFlux = 0.0;
    private static int waveCount = 0;
    private static int oscillationCount = 0;
    // Rolling observation history for simple detectors
    private static final int HISTORY_LENGTH = 8;
    private static final Map<String, Deque<Double>> chunkActivationHistory = new HashMap<>();
    private static final Map<String, Deque<Double>> chunkMemoryHistory = new HashMap<>();
    private static final Map<String, Deque<Double>> chunkDepthHistory = new HashMap<>();
    private static final Map<Integer, Deque<Double>> regionActivationHistory = new HashMap<>();
    private static final Map<Integer, Deque<Double>> regionDepthHistory = new HashMap<>();
    private static final Map<String, Deque<Double>> regionPairTransportHistory = new HashMap<>();
    private static final Set<String> announcedHotspots = new HashSet<>();
    private static double previousCentroidX = Double.NaN;
    private static double previousCentroidZ = Double.NaN;

    private ResearchObserver() {}

    public static void tick(MinecraftServer server, long tick) {
        if (tick % 40 != 0) {
            return;
        }
        for (ServerWorld world : server.getWorlds()) {
            ChunkTensionData data = ChunkTensionData.getServerState(world);
            List<ResearchObservationEntry> entries = new ArrayList<>();
            for (ChunkPos pos : data.getActiveChunkPositions()) {
                if (!world.isChunkLoaded(pos.x, pos.z)) continue;
                double tension = ChunkTensionManager.getLocalTension(world, pos);
                ChunkTensionData.ChunkState state = data.getChunkState(pos);
                SubstrateState substrateState = data.getOrCreateSubstrateState(pos);
                Region region = RegionManager.getRegionForChunk(world, pos);
                int regionId = region != null ? region.getId() : 0;
                double memory = substrateState.getMemory();
                double activation = substrateState.getActivation();
                double baseline = substrateState.getBaseline();
                double transport = substrateState.getTransport();
                ChunkInterpretation interpretation = InterpretationService.get(world, pos);
                double transportX = transport;
                double transportZ = 0.0;
                if (interpretation != null && interpretation.semantics != null) {
                    transportX = transport * interpretation.semantics.transportBiasX;
                    transportZ = transport * interpretation.semantics.transportBiasZ;
                }
                entries.add(new ResearchObservationEntry(pos.x, pos.z, tension, state.ordinal(), activation, memory, transportX, transportZ, substrateState.getFieldDepth(), baseline, regionId));
            }
            detectPhenomena(world, entries, tick);
        }
    }

    private static void detectPhenomena(ServerWorld world, List<ResearchObservationEntry> entries, long tick) {
        if (entries.isEmpty()) {
            return;
        }
        int activeRegions = 0;
        int largestRegion = 0;
        int largestRegionChunks = 0;
        int scarCount = 0;
        double maxTension = 0.0;
        double meanActivation = 0.0;
        double meanMemory = 0.0;
        double meanFieldDepth = 0.0;
        double transportFlux = 0.0;
        double regionCenterX = 0.0;
        double regionCenterZ = 0.0;
        Set<Integer> seenRegions = new HashSet<>();
        double transportFluxX = 0.0;
        double transportFluxZ = 0.0;
        for (ResearchObservationEntry entry : entries) {
            meanActivation += entry.activation;
            meanMemory += entry.memory;
            meanFieldDepth += entry.fieldDepth;
            transportFlux += Math.hypot(entry.transportX, entry.transportZ);
            transportFluxX += entry.transportX;
            transportFluxZ += entry.transportZ;
            if (entry.regionId > 0) {
                seenRegions.add(entry.regionId);
            }
            if (entry.memory > 0) {
                scarCount++;
            }
            if (entry.tension > maxTension) {
                maxTension = entry.tension;
                largestRegion = entry.regionId;
            }
        }
        activeRegions = seenRegions.size();
        meanActivation /= Math.max(1, entries.size());
        meanMemory /= Math.max(1, entries.size());
        meanFieldDepth /= Math.max(1, entries.size());
        transportFlux /= Math.max(1, entries.size());
        transportFluxX /= Math.max(1, entries.size());
        transportFluxZ /= Math.max(1, entries.size());

        for (Region region : RegionManager.getRegions()) {
            if (region.getChunkCount() > largestRegionChunks) {
                largestRegionChunks = region.getChunkCount();
                largestRegion = region.getId();
                regionCenterX = region.getCentroidX();
                regionCenterZ = region.getCentroidZ();
            }
        }
        if (tick - lastNarrationTick > 1200 || narrativeLog.isEmpty()) {
            addNarration(String.format("Observation tick=%d activeRegions=%d maxTension=%.3f meanActivation=%.3f meanMemory=%.3f transportFlux=%.3f", tick, activeRegions, maxTension, meanActivation, meanMemory, transportFlux));
            lastNarrationTick = tick;
        }
        // Update rolling histories (per-chunk and per-region)
        updateHistories(entries);

        // Run detectors that produce higher-level narratives
        detectPersistentHotspots(entries, tick);
        detectActivationWave(entries, tick, meanActivation, transportFlux);
        detectFieldDepthGrowth(entries, tick);
        detectRegionalStabilization(entries, tick);
        detectRecovery(entries, tick);
            detectTransportCorridors(world, entries, tick);
        detectSynchronization(entries, tick);
        detectLongTermTrends(entries, tick);
        if (transportFlux > previousTransportFlux * 1.35 + 0.02 && meanActivation > 0.7) {
            waveCount++;
            addNarration(String.format("Wave detected at tick %d (flux %.3f)", tick, transportFlux));
        }
        if (previousMeanActivation > 0.0 && Math.signum(meanActivation - previousMeanActivation) != Math.signum(previousMeanActivation - previousTransportFlux)) {
            oscillationCount++;
            addNarration(String.format("Oscillation inferred at tick %d", tick));
        }
        previousMeanActivation = meanActivation;
        previousTransportFlux = transportFlux;
        ResearchSnapshot snapshot = new ResearchSnapshot(
            ResearchConfig.getVisualMode(),
            narrativeLog.isEmpty() ? "" : narrativeLog.get(narrativeLog.size() - 1),
            activeRegions,
            largestRegionChunks,
            scarCount,
            waveCount,
            oscillationCount,
            meanFieldDepth,
            transportFluxX,
            transportFluxZ,
            regionCenterX,
            regionCenterZ,
            Math.max(1, activeRegions)
        );
        ResearchSyncPacket.sendToAllPlayers(world.getServer(), snapshot);
        if (maxTension > 1.4) {
            addNarration(String.format("High activation detected at tick %d (%.3f)", tick, maxTension));
        }
        if (meanMemory > 1.0) {
            addNarration(String.format("Persistent scar memory elevated at tick %d", tick));
        }
        if (transportFlux > 0.12) {
            addNarration(String.format("Transport flux intensified at tick %d", tick));
        }
        if (activeRegions > 1 && TensionManager.getTension() > 0.8) {
            addNarration(String.format("Multi-region activity observed at tick %d", tick));
        }
    }

    public static List<String> getNarrativeLog() {
        return new ArrayList<>(narrativeLog);
    }

    public static void clearNarrativeLog() {
        narrativeLog.clear();
    }

    private static void addNarration(String line) {
        narrativeLog.add(line);
        if (narrativeLog.size() > 128) {
            narrativeLog.remove(0);
        }
        System.out.println("[RESEARCH_OBSERVER] " + line);
    }

    // ------ Detector helpers ------
    private static void pushHistory(Map<String, Deque<Double>> map, String key, double value) {
        Deque<Double> dq = map.computeIfAbsent(key, k -> new ArrayDeque<>());
        dq.addLast(value);
        while (dq.size() > HISTORY_LENGTH) dq.removeFirst();
    }

    private static void pushRegionHistory(Map<Integer, Deque<Double>> map, int key, double value) {
        Deque<Double> dq = map.computeIfAbsent(key, k -> new ArrayDeque<>());
        dq.addLast(value);
        while (dq.size() > HISTORY_LENGTH) dq.removeFirst();
    }

    private static void updateHistories(List<ResearchObservationEntry> entries) {
        // per-chunk histories
        Map<String, ResearchObservationEntry> byCoord = new HashMap<>();
        for (ResearchObservationEntry e : entries) {
            String ck = e.x + "," + e.z;
            pushHistory(chunkActivationHistory, ck, e.activation);
            pushHistory(chunkMemoryHistory, ck, e.memory);
            pushHistory(chunkDepthHistory, ck, e.fieldDepth);
            byCoord.put(ck, e);
        }
        // per-region mean activation & depth
        Map<Integer, List<ResearchObservationEntry>> perRegion = new HashMap<>();
        for (ResearchObservationEntry e : entries) {
            perRegion.computeIfAbsent(e.regionId, r -> new ArrayList<>()).add(e);
        }
        for (Map.Entry<Integer, List<ResearchObservationEntry>> re : perRegion.entrySet()) {
            int rid = re.getKey();
            double sumA = 0.0, sumD = 0.0;
            for (ResearchObservationEntry e : re.getValue()) {
                sumA += e.activation;
                sumD += e.fieldDepth;
            }
            pushRegionHistory(regionActivationHistory, rid, sumA / Math.max(1, re.getValue().size()));
            pushRegionHistory(regionDepthHistory, rid, sumD / Math.max(1, re.getValue().size()));
        }
        // region-pair transport: detect neighbor pairs where chunks with high transport sit on region boundary
        for (ResearchObservationEntry e : entries) {
            if (Math.abs(e.transportX) + Math.abs(e.transportZ) < 0.06) continue;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    String nk = (e.x + dx) + "," + (e.z + dz);
                    // neighbor may not be present in this tick's entries
                    // we look up in chunkActivationHistory presence as proxy
                    Deque<Double> nd = chunkActivationHistory.get(nk);
                    if (nd == null || nd.isEmpty()) continue;
                    // assume neighbor region id lookup via recent entries map not available here; skip detailed pair detection
                }
            }
        }
    }

    private static void detectPersistentHotspots(List<ResearchObservationEntry> entries, long tick) {
        final double HOTSPOT_THRESH = 0.7;
        final int DURATION = 6;
        Set<String> currentlyHot = new HashSet<>();
        for (ResearchObservationEntry e : entries) {
            String ck = e.x + "," + e.z;
            Deque<Double> h = chunkActivationHistory.getOrDefault(ck, new ArrayDeque<>());
            if (h.size() >= DURATION) {
                double sum = 0.0;
                int c = 0;
                for (double v : h) { sum += v; c++; }
                double avg = c > 0 ? sum / c : 0.0;
                if (avg >= HOTSPOT_THRESH || e.memory > 1.0 || e.fieldDepth > 0.5) {
                    currentlyHot.add(ck);
                    if (!announcedHotspots.contains(ck)) {
                        addNarration(String.format("Persistent hotspot detected near (%d, %d).", e.x * 16, e.z * 16));
                        announcedHotspots.add(ck);
                    }
                }
            }
        }
        // forget hotspots that have cooled
        announcedHotspots.removeIf(hk -> !currentlyHot.contains(hk));
    }

    private static void detectActivationWave(List<ResearchObservationEntry> entries, long tick, double meanActivation, double transportFlux) {
        // centroid of top-k active chunks
        int K = Math.min(8, entries.size());
        if (K <= 0) return;
        entries.sort((a, b) -> Double.compare(b.activation, a.activation));
        double sx = 0.0, sz = 0.0, sw = 0.0;
        for (int i = 0; i < K; i++) {
            ResearchObservationEntry e = entries.get(i);
            double w = e.activation + 1e-6;
            sx += e.x * w;
            sz += e.z * w;
            sw += w;
        }
        double cx = sx / sw;
        double cz = sz / sw;
        if (!Double.isNaN(previousCentroidX)) {
            double dx = cx - previousCentroidX;
            double dz = cz - previousCentroidZ;
            double dist = Math.hypot(dx, dz);
            if (dist > 0.9 && meanActivation > 0.25 && transportFlux > 0.05) {
                String dir = dominantDirection(dx, dz);
                addNarration(String.format("Activation wave propagating %s.", dir));
            }
        }
        previousCentroidX = cx;
        previousCentroidZ = cz;
    }

    private static String dominantDirection(double dx, double dz) {
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? "east" : "west";
        } else {
            return dz > 0 ? "south" : "north";
        }
    }

    private static void detectFieldDepthGrowth(List<ResearchObservationEntry> entries, long tick) {
        final int GROW_LEN = 5;
        for (Map.Entry<Integer, Deque<Double>> e : regionDepthHistory.entrySet()) {
            Deque<Double> dq = e.getValue();
            if (dq.size() < GROW_LEN) continue;
            boolean growing = true;
            Double prev = null;
            for (double v : dq) {
                if (prev != null && v <= prev + 1e-6) { growing = false; break; }
                prev = v;
            }
            if (growing) {
                addNarration(String.format("Field depth increasing steadily in Region %d. Long-term substrate restructuring underway.", e.getKey()));
            }
        }
    }

    private static void detectRegionalStabilization(List<ResearchObservationEntry> entries, long tick) {
        final int WINDOW = 6;
        for (Map.Entry<Integer, Deque<Double>> e : regionActivationHistory.entrySet()) {
            Deque<Double> dq = e.getValue();
            if (dq.size() < WINDOW) continue;
            double mean = 0.0;
            for (double v: dq) mean += v;
            mean /= dq.size();
            double var = 0.0;
            for (double v: dq) var += (v-mean)*(v-mean);
            var /= dq.size();
            // simple stabilization: low variance and moderate activation
            if (var < 0.02 && mean > 0.2 && mean < 1.2) {
                addNarration(String.format("Region %d transitioning toward stable equilibrium.", e.getKey()));
            }
        }
    }

    private static void detectRecovery(List<ResearchObservationEntry> entries, long tick) {
        final int WINDOW = 6;
        for (Map.Entry<Integer, Deque<Double>> e : regionActivationHistory.entrySet()) {
            int rid = e.getKey();
            Deque<Double> dq = e.getValue();
            Deque<Double> mdq = regionDepthHistory.getOrDefault(rid, new ArrayDeque<>());
            if (dq.size() < WINDOW || mdq.isEmpty()) continue;
            // decreasing activation but memory/depth nonzero
            Double first = dq.peekFirst();
            Double last = dq.peekLast();
            if (first != null && last != null && last < first * 0.6) {
                double recentDepth = mdq.peekLast();
                if (recentDepth > 0.05) {
                    addNarration(String.format("Previously active region %d recovering while retaining historical memory.", rid));
                }
            }
        }
    }

    private static void detectTransportCorridors(ServerWorld world, List<ResearchObservationEntry> entries, long tick) {
        int corridorChunks = 0;
        int longestChain = 0;
        String chainDirection = null;
        for (ResearchObservationEntry entry : entries) {
            ChunkInterpretation interpretation = InterpretationService.get(world, new ChunkPos(entry.x, entry.z));
            double magnitude = interpretation != null && interpretation.semantics != null
                ? interpretation.semantics.corridorStrength
                : Math.hypot(entry.transportX, entry.transportZ);
            if (magnitude >= InterpretationThresholds.TRANSPORT_CORRIDOR) {
                corridorChunks++;
                int chain = corridorChainLength(world, entry.x, entry.z);
                if (chain > longestChain) {
                    longestChain = chain;
                    ChunkInterpretation self = InterpretationService.get(world, new ChunkPos(entry.x, entry.z));
                    if (self != null) {
                        var dir = com.utdmod.interpretation.SpatialBehaviorHelper.resolveCorridorDirection(world, self);
                        if (dir != null) {
                            chainDirection = dominantDirection(dir.stepX(), dir.stepZ());
                        }
                    }
                }
            }
        }
        if (corridorChunks >= 2) {
            if (longestChain >= 2 && chainDirection != null) {
                addNarration(String.format(
                    "Transport corridor chain (%d chunks, %s) at tick %d (%d corridor chunks).",
                    longestChain, chainDirection, tick, corridorChunks));
            } else {
                addNarration(String.format("Transport corridor network detected at tick %d (%d chunks).", tick, corridorChunks));
            }
        }
    }

    private static int corridorChainLength(ServerWorld world, int startX, int startZ) {
        ChunkInterpretation start = InterpretationService.get(world, new ChunkPos(startX, startZ));
        if (start == null || start.semantics == null
            || start.semantics.corridorStrength < InterpretationThresholds.TRANSPORT_CORRIDOR) {
            return 0;
        }
        var dir = com.utdmod.interpretation.SpatialBehaviorHelper.resolveCorridorDirection(world, start);
        if (dir == null || (dir.stepX() == 0 && dir.stepZ() == 0)) {
            return 1;
        }
        int length = 1;
        int cx = startX;
        int cz = startZ;
        for (int step = 0; step < 8; step++) {
            cx += dir.stepX();
            cz += dir.stepZ();
            if (!world.isChunkLoaded(cx, cz)) {
                break;
            }
            ChunkInterpretation next = InterpretationService.get(world, new ChunkPos(cx, cz));
            if (next == null || next.semantics == null
                || next.semantics.corridorStrength < InterpretationThresholds.TRANSPORT_CORRIDOR * 0.75) {
                break;
            }
            length++;
        }
        return length;
    }

    private static void detectSynchronization(List<ResearchObservationEntry> entries, long tick) {
        // Look for multiple regions with similar recent activation trends
        final double CORR_THRESH = 0.85;
        List<Integer> regionIds = new ArrayList<>(regionActivationHistory.keySet());
        for (int i = 0; i < regionIds.size(); i++) {
            for (int j = i+1; j < regionIds.size(); j++) {
                int a = regionIds.get(i), b = regionIds.get(j);
                Deque<Double> da = regionActivationHistory.get(a);
                Deque<Double> db = regionActivationHistory.get(b);
                if (da == null || db == null) continue;
                if (da.size() < 4 || db.size() < 4) continue;
                double corr = pearsonCorrelation(da, db);
                if (!Double.isNaN(corr) && corr > CORR_THRESH) {
                    addNarration(String.format("Regional synchronization emerging between %d and %d.", a, b));
                }
            }
        }
    }

    private static double pearsonCorrelation(Deque<Double> a, Deque<Double> b) {
        int n = Math.min(a.size(), b.size());
        if (n < 3) return Double.NaN;
        Double[] aa = a.toArray(new Double[0]);
        Double[] bb = b.toArray(new Double[0]);
        int offsetA = aa.length - n;
        int offsetB = bb.length - n;
        double meanA = 0.0, meanB = 0.0;
        for (int i = 0; i < n; i++) { meanA += aa[offsetA + i]; meanB += bb[offsetB + i]; }
        meanA /= n; meanB /= n;
        double num = 0.0, denA = 0.0, denB = 0.0;
        for (int i = 0; i < n; i++) {
            double da = aa[offsetA + i] - meanA;
            double db = bb[offsetB + i] - meanB;
            num += da * db;
            denA += da * da;
            denB += db * db;
        }
        double denom = Math.sqrt(denA * denB);
        return denom == 0.0 ? Double.NaN : num / denom;
    }

    private static void detectLongTermTrends(List<ResearchObservationEntry> entries, long tick) {
        if (oscillationCount > 8) {
            addNarration("Repeated oscillations observed across the field; system showing cyclical dynamics.");
        }
    }
}
