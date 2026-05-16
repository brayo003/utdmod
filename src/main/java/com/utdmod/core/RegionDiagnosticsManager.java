package com.utdmod.core;

import com.utdmod.diag.DiagnosticLogs;
import com.utdmod.tension.ChunkTensionData;
import com.utdmod.tension.ChunkTensionManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

import java.util.HashMap;
import java.util.Map;

/**
 * 8×8 chunk regions from loaded tension entries only. Refreshed every 200 server ticks.
 */
public final class RegionDiagnosticsManager {

    public static final int REGION_SIZE = 8;

    public enum EcoState {
        CALM,
        STRAINED,
        FRACTURED
    }

    public static final class RegionSnapshot {
        public final int regionX;
        public final int regionZ;
        public final double avg;
        public final double max;
        public final int strained;
        public final int fractured;
        public final boolean storm;
        /** 0–1 estimate from regional tension only (no entity scans). */
        public final double hostileBias;

        RegionSnapshot(int regionX, int regionZ, double avg, double max, int strained, int fractured, boolean storm, double hostileBias) {
            this.regionX = regionX;
            this.regionZ = regionZ;
            this.avg = avg;
            this.max = max;
            this.strained = strained;
            this.fractured = fractured;
            this.storm = storm;
            this.hostileBias = hostileBias;
        }

        public EcoState ecoState() {
            if (max > 1.35) return EcoState.FRACTURED;
            if (avg > 1.0) return EcoState.STRAINED;
            return EcoState.CALM;
        }
    }

    private static final Map<Long, RegionSnapshot> CACHE = new HashMap<>(256);
    private static long lastSampleTick = Long.MIN_VALUE;

    private RegionDiagnosticsManager() {}

    private static long pack(int rx, int rz) {
        return (((long) rx) << 32) | (rz & 0xffffffffL);
    }

    public static RegionSnapshot get(int chunkX, int chunkZ) {
        int rx = Math.floorDiv(chunkX, REGION_SIZE);
        int rz = Math.floorDiv(chunkZ, REGION_SIZE);
        return CACHE.get(pack(rx, rz));
    }

    /** Neighboring 8×8 region center (chunk coords of region origin) with highest avg; fallback this region. */
    public static ChunkPos movingTowardHotterRegion(int regionX, int regionZ) {
        RegionSnapshot here = CACHE.get(pack(regionX, regionZ));
        double best = here != null ? here.avg : 0.0;
        int bx = regionX;
        int bz = regionZ;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                RegionSnapshot s = CACHE.get(pack(regionX + dx, regionZ + dz));
                if (s != null && s.avg > best) {
                    best = s.avg;
                    bx = regionX + dx;
                    bz = regionZ + dz;
                }
            }
        }
        return new ChunkPos(bx, bz);
    }

    private static double tensionHostileBias(double avg, double max, boolean storm) {
        return Math.min(1.0, Math.max(0.0, (avg - 0.55) * 0.32 + max * 0.12 + (storm ? 0.14 : 0.0)));
    }

    private static final class Agg {
        double sum;
        int n;
        double max;
        int strained;
        int fractured;
        boolean storm;
    }

    public static void refresh(MinecraftServer server, long tick) {
        CACHE.clear();
        lastSampleTick = tick;

        for (ServerWorld sw : server.getWorlds()) {
            ChunkTensionData data = ChunkTensionData.getServerState(sw);
            Map<ChunkPos, Double> map = data.getTensionMap();
            Map<Long, Agg> aggs = new HashMap<>(128);

            for (Map.Entry<ChunkPos, Double> e : map.entrySet()) {
                ChunkPos pos = e.getKey();
                if (!sw.isChunkLoaded(pos.x, pos.z)) continue;
                double t = e.getValue();
                if (t <= 0.0) continue;
                int rx = Math.floorDiv(pos.x, REGION_SIZE);
                int rz = Math.floorDiv(pos.z, REGION_SIZE);
                long key = pack(rx, rz);
                Agg a = aggs.computeIfAbsent(key, k -> new Agg());
                a.sum += t;
                a.n++;
                a.max = Math.max(a.max, t);
                ChunkTensionData.ChunkState st = ChunkTensionManager.getChunkState(sw, pos);
                if (st == ChunkTensionData.ChunkState.FRACTURED || st == ChunkTensionData.ChunkState.DECOUPLED) {
                    a.fractured++;
                } else if (st == ChunkTensionData.ChunkState.STRAINED) {
                    a.strained++;
                }
                if (data.isStormActive(pos)) {
                    a.storm = true;
                }
            }

            for (Map.Entry<Long, Agg> e : aggs.entrySet()) {
                Agg a = e.getValue();
                if (a.n <= 0) continue;
                long key = e.getKey();
                int rx = (int) (key >> 32);
                int rz = (int) key;
                double avg = a.sum / a.n;
                double hostileBias = tensionHostileBias(avg, a.max, a.storm);
                RegionSnapshot snap = new RegionSnapshot(rx, rz, avg, a.max, a.strained, a.fractured, a.storm, hostileBias);
                CACHE.put(key, snap);
                DiagnosticLogs.region(tick, rx, rz, avg, a.max, a.strained, a.fractured, a.storm, hostileBias);
            }
        }
    }

    public static long getLastSampleTick() {
        return lastSampleTick;
    }
}
